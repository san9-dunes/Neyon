package io.github.landwarderer.neyon.suggestions.domain

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import io.github.landwarderer.neyon.core.db.MangaDatabase
import io.github.landwarderer.neyon.core.db.entity.toEntities
import io.github.landwarderer.neyon.core.db.entity.toEntity
import io.github.landwarderer.neyon.core.db.entity.toManga
import io.github.landwarderer.neyon.core.db.entity.toMangaTags
import io.github.landwarderer.neyon.core.db.entity.toMangaTagsList
import io.github.landwarderer.neyon.core.model.toMangaSources
import io.github.landwarderer.neyon.core.util.ext.mapItems
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import io.github.landwarderer.neyon.suggestions.data.SuggestionEntity
import io.github.landwarderer.neyon.suggestions.data.SuggestionWithManga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionRepository @Inject constructor(
	private val db: MangaDatabase,
) {

	fun observeAll(): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll().mapItems {
			it.toManga()
		}
	}

	fun observeAll(limit: Int, filterOptions: Set<ListFilterOption>): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll(limit, filterOptions).mapItems {
			it.toManga()
		}
	}

	suspend fun getRandomList(limit: Int, forceRefresh: Boolean = false): List<Manga> {
		return db.getSuggestionDao().getRandom(limit).map { it.toManga() }
	}

	suspend fun clear() {
		db.getSuggestionDao().deleteAll()
	}

	suspend fun isEmpty(): Boolean {
		return db.getSuggestionDao().count() == 0
	}

	suspend fun getTopTags(limit: Int): List<MangaTag> {
		return db.getSuggestionDao().getTopTags(limit)
			.toMangaTagsList()
	}

	suspend fun getTopSources(limit: Int): List<MangaSource> {
		return db.getSuggestionDao().getTopSources(limit)
			.toMangaSources()
	}

	suspend fun replace(suggestions: Iterable<MangaSuggestion>) {
		// Convert to List safely to ensure multi-pass iteration works correctly.
		val suggestionList = suggestions.toList()

		val allTags = suggestionList.flatMap { it.manga.tags.toEntities() }.distinctBy { it.id }
		val mangasWithTags = suggestionList.map { suggestion ->
			val manga = suggestion.manga
			Pair(manga.toEntity(), manga.tags.toEntities())
		}
		val suggestionEntities = suggestionList.map { suggestion ->
			SuggestionEntity(
				mangaId = suggestion.manga.id,
				relevance = suggestion.relevance,
				reason = suggestion.reason,
				createdAt = System.currentTimeMillis(),
			)
		}

		db.withTransaction {
			db.getSuggestionDao().deleteAll()

			// Bolt Performance Optimization:
			// Replaced iterative `suggestions.forEach { upsert(...) }` with bulk DAO operations.
			// Impact: Solves an N+1 SQLite transaction bottleneck by mapping all entities
			// first and batch inserting them in a single sweep.
			if (allTags.isNotEmpty()) {
				db.getTagsDao().upsert(allTags)
			}
			db.getMangaDao().upsertAllWithTags(mangasWithTags)
			if (suggestionEntities.isNotEmpty()) {
				db.getSuggestionDao().upsertAll(suggestionEntities)
			}
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
