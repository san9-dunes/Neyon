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
import io.github.landwarderer.neyon.list.domain.ListSortOrder
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
        var cachedSortOrder: ListSortOrder? = null

        suspend fun mixFeed(forceGenreTag: String? = null, forceRefresh: Boolean = false, listSortOrder: ListSortOrder): List<Manga> = supervisorScope {
                val whitelistNames = appSettings.suggestionSourcesWhitelist     
                if (whitelistNames.isEmpty()) return@supervisorScope emptyList()

                if (cachedWhitelist != whitelistNames || cachedSortOrder != listSortOrder) {
                        cachedFeed = null
                        cachedWhitelist = whitelistNames
                        cachedSortOrder = listSortOrder
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
                                fetch(source, forceGenreTag, tagsBlacklist, listSortOrder)
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
                listSortOrder: ListSortOrder,
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

                        val targetPrimarySort = if (listSortOrder == ListSortOrder.POPULARITY) SortOrder.POPULARITY else SortOrder.UPDATED
                        val targetSecondarySort = if (listSortOrder == ListSortOrder.POPULARITY) SortOrder.UPDATED else SortOrder.POPULARITY
                        
                        val order = if (forceGenreTag == null) {
                                if (targetPrimarySort in availableOrders) targetPrimarySort else availableOrders.firstOrNull()
                        } else {
                                // Default user request: "when i click a genre/tag it was supposed to show in popular order"
                                // Always try to display POPULARITY first if forced genre tag is used unless ListSortOrder is explicitly set differently
                                val forcedSort = if (listSortOrder == ListSortOrder.POPULARITY || listSortOrder == ListSortOrder.UPDATED) targetPrimarySort else SortOrder.POPULARITY
                                val fallbackSort = if (forcedSort == SortOrder.POPULARITY) SortOrder.UPDATED else SortOrder.POPULARITY
                                listOf(forcedSort, fallbackSort).firstOrNull { it in availableOrders } ?: availableOrders.firstOrNull()
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
