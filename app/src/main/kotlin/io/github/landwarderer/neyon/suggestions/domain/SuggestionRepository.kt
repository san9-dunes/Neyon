package io.github.landwarderer.neyon.suggestions.domain

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import io.github.landwarderer.neyon.core.db.MangaDatabase
import io.github.landwarderer.neyon.core.db.entity.toEntities
import io.github.landwarderer.neyon.core.db.entity.toEntity
import io.github.landwarderer.neyon.core.db.entity.toManga
import io.github.landwarderer.neyon.core.db.entity.toMangaTags
import io.github.landwarderer.neyon.core.db.entity.toMangaTagsList
import io.github.landwarderer.neyon.core.db.entity.MangaEntity
import io.github.landwarderer.neyon.core.db.entity.TagEntity
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
        val allTags = mutableListOf<TagEntity>()
        val mangaEntities = mutableListOf<MangaEntity>()
        val tagsMap = mutableMapOf<Long, List<TagEntity>>()
        val suggestionEntities = mutableListOf<SuggestionEntity>()
        val time = System.currentTimeMillis()

        suggestions.forEach { suggestion ->
            val manga = suggestion.manga
            val tags = manga.tags.toEntities()

            allTags.addAll(tags)
            val mangaEntity = manga.toEntity()
            mangaEntities.add(mangaEntity)
            tagsMap[manga.id] = tags

            suggestionEntities.add(
                SuggestionEntity(
                    mangaId = manga.id,
                    relevance = suggestion.relevance,
                    reason = suggestion.reason,
                    createdAt = time,
                )
            )
        }

		db.withTransaction {
			db.getSuggestionDao().deleteAll()
            // Bolt Performance Optimization:
            // Replaced iterative DB inserts `.forEach { upsert(it) }` with bulk operations.
            // Impact: Resolves N+1 query problem, drastically reducing SQLite transaction overhead during suggestion replacement.
            db.getTagsDao().upsert(allTags.distinctBy { it.id })
            db.getMangaDao().upsertAllWithTags(mangaEntities, tagsMap)
            db.getSuggestionDao().upsertAll(suggestionEntities)
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
