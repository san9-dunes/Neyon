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
		val allTags = mutableSetOf<io.github.landwarderer.neyon.core.db.entity.TagEntity>()
		val mangas = mutableListOf<io.github.landwarderer.neyon.core.db.entity.MangaEntity>()
		val mangaTagsRelations = mutableListOf<io.github.landwarderer.neyon.core.db.entity.MangaTagsEntity>()
		val suggestionEntities = mutableListOf<SuggestionEntity>()
		val mangaIdsWithTags = mutableListOf<Long>()

		val createdAt = System.currentTimeMillis()

		suggestions.forEach { suggestion ->
			val manga = suggestion.manga
			val tags = manga.tags.toEntities()

			allTags.addAll(tags)
			mangas.add(manga.toEntity())

			// Unconditionally add ID to clear relations for this manga before updating
			mangaIdsWithTags.add(manga.id)

			if (tags.isNotEmpty()) {
				mangaTagsRelations.addAll(tags.map {
					io.github.landwarderer.neyon.core.db.entity.MangaTagsEntity(manga.id, it.id)
				})
			}

			suggestionEntities.add(
				SuggestionEntity(
					mangaId = manga.id,
					relevance = suggestion.relevance,
					reason = suggestion.reason,
					createdAt = createdAt,
				)
			)
		}

		db.withTransaction {
			db.getSuggestionDao().deleteAll()

			if (allTags.isNotEmpty()) {
				db.getTagsDao().upsert(allTags)
			}
			if (mangas.isNotEmpty()) {
				db.getMangaDao().upsert(mangas)
			}
			if (mangaIdsWithTags.isNotEmpty()) {
				db.getMangaDao().clearTagRelations(mangaIdsWithTags)
				db.getMangaDao().insertTagRelations(mangaTagsRelations)
			}
			if (suggestionEntities.isNotEmpty()) {
				db.getSuggestionDao().upsert(suggestionEntities)
			}
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
