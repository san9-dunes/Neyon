package io.github.landwarderer.neyon.suggestions.domain

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import io.github.landwarderer.neyon.core.db.MangaDatabase
import io.github.landwarderer.neyon.core.db.entity.toEntities
import io.github.landwarderer.neyon.core.db.entity.toEntity
import io.github.landwarderer.neyon.core.db.entity.toManga
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

	private var memoryCache: List<Manga>? = null

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
		if (!forceRefresh && !memoryCache.isNullOrEmpty()) {
			// If cached list is bigger or equal to the limit, slice it. Otherwise return all.
			return if (memoryCache!!.size >= limit) {
				memoryCache!!.shuffled().take(limit)
			} else {
				memoryCache!!
			}
		}
		
		val result = db.getSuggestionDao().getRandom(limit).map {
			it.toManga()
		}
		
		// Update our memory cache with the new fetch
		memoryCache = result
		return result
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
			suggestions.forEach { (manga, relevance) ->
				val tags = manga.tags.toEntities()
				db.getTagsDao().upsert(tags)
				db.getMangaDao().upsert(manga.toEntity(), tags)
				db.getSuggestionDao().upsert(
					SuggestionEntity(
						mangaId = manga.id,
						relevance = manga.relevance,
						reason = manga.reason,
						createdAt = System.currentTimeMillis(),
					),
				)
			}
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(emptySet(), null)
}
