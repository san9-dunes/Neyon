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
		db.withTransaction {
			db.getSuggestionDao().deleteAll()
			val allTags = mutableListOf<io.github.landwarderer.neyon.core.db.entity.TagEntity>()
			val suggestionEntities = mutableListOf<SuggestionEntity>()

			// 1. Collect and insert all tags first to avoid Foreign Key constraint violations
			for (suggestion in suggestions) {
				allTags.addAll(suggestion.manga.tags.toEntities())
			}
			db.getTagsDao().upsert(allTags.distinctBy { it.id })

			// 2. Iterate again to insert manga (and tag relations) and collect suggestions
			for (suggestion in suggestions) {
				val manga = suggestion.manga
				val tags = manga.tags.toEntities()
				db.getMangaDao().upsert(manga.toEntity(), tags)
				suggestionEntities.add(
					SuggestionEntity(
						mangaId = manga.id,
						relevance = suggestion.relevance,
						reason = suggestion.reason,
						createdAt = System.currentTimeMillis(),
					)
				)
			}

			// 3. Bulk insert suggestions
			db.getSuggestionDao().upsertAll(suggestionEntities)
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
