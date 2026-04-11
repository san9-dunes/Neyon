package io.github.landwarderer.neyon.suggestions.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.model.distinctById
import io.github.landwarderer.neyon.core.model.isNsfw
import io.github.landwarderer.neyon.core.util.ext.asArrayList
import io.github.landwarderer.neyon.suggestions.domain.TagsBlacklist
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.almostEquals
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedAggregator @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val appSettings: AppSettings,
) {

	private val preferredSortOrders = listOf(SortOrder.UPDATED, SortOrder.POPULARITY)

        var cachedFeed: List<Manga>? = null
        private var cachedWhitelist: Set<String>? = null

        suspend fun mixFeed(forceGenreTag: String? = null, forceRefresh: Boolean = false): List<Manga> = supervisorScope {
                val whitelistNames = appSettings.suggestionSourcesWhitelist     
                if (whitelistNames.isEmpty()) return@supervisorScope emptyList()

                if (cachedWhitelist != whitelistNames) {
                        cachedFeed = null
                        cachedWhitelist = whitelistNames
                }

                if (!forceRefresh && forceGenreTag == null && !cachedFeed.isNullOrEmpty()) {
                        return@supervisorScope cachedFeed!!
                }

                val allEnabled = sourcesRepository.getEnabledSources()
                val sourcesToUse = allEnabled.filter { it.name in whitelistNames }.shuffled().take(8)

                if (sourcesToUse.isEmpty()) return@supervisorScope emptyList()  

                val tagsBlacklist = TagsBlacklist(appSettings.suggestionsTagsBlacklist, 0.4f)

                val queries = sourcesToUse.map { source ->
                        async(Dispatchers.IO) {
                                fetch(source, forceGenreTag, tagsBlacklist)
                        }
                }

                val results = queries.awaitAll()

                val mergedList = mutableListOf<Manga>()
                val iterators = results.map { it.iterator() }

                var hasMore = true
                while (hasMore) {
                        hasMore = false
                        for (it in iterators) {
                                for (i in 0 until 5) {
                                        if (it.hasNext()) {
                                                mergedList.add(it.next())
                                                hasMore = true
                                        }
                                }
                        }
                }

                val finalFeed = mergedList.distinctById()
                if (forceGenreTag == null) {
                        cachedFeed = finalFeed
                }
                finalFeed
        }

        private suspend fun fetch(
                source: MangaSource,
                forceGenreTag: String?,
                tagsBlacklist: TagsBlacklist,
        ): List<Manga> = runCatchingCancellable {
                withTimeoutOrNull(5000L) {
                        val repository = mangaRepositoryFactory.create(source)  
                        val availableOrders = repository.sortOrders
                        
                        val filter = if (forceGenreTag != null) {
                                val matchedTag = repository.getFilterOptions().availableTags.find { x ->
                                        x.title.almostEquals(forceGenreTag, 0.7f)
                                }
                                if (matchedTag != null) {
                                        MangaListFilter(tags = setOf(matchedTag))
                                } else {
                                        MangaListFilter(query = forceGenreTag)
                                }
                        } else {
                                MangaListFilter() 
                        }

                        val order = if (forceGenreTag == null) {
                                if (SortOrder.UPDATED in availableOrders) SortOrder.UPDATED else availableOrders.firstOrNull()
                        } else {
                                preferredSortOrders.firstOrNull { it in availableOrders } ?: availableOrders.firstOrNull()
                        }

                        val list = repository.getList(
                                offset = 0,
                                order = order,
                                filter = filter
                        ).asArrayList()

                        if (appSettings.isSuggestionsExcludeNsfw) {
                                list.removeAll { it.isNsfw() }
                        }
                        if (tagsBlacklist.isNotEmpty()) {
                                list.removeAll { manga -> manga in tagsBlacklist }
                        }
                        list
                } ?: emptyList()
        }.getOrElse { emptyList() }
}
