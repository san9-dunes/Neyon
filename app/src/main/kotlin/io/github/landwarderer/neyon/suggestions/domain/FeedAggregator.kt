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
import io.github.landwarderer.neyon.suggestions.domain.MangaSuggestion
import org.koitharu.kotatsu.parsers.util.almostEquals
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedAggregator @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val appSettings: AppSettings,
	private val getUserAffinityTagsUseCase: GetUserAffinityTagsUseCase,
) {

        @Volatile var memoryCache: List<MangaSuggestion>? = null
            private set
        @Volatile private var cachedWhitelist: Set<String>? = null
        @Volatile private var cachedSortOrder: ListSortOrder? = null

        suspend fun mixFeed(forceGenreTag: String? = null, forceRefresh: Boolean = false, listSortOrder: ListSortOrder): List<MangaSuggestion> = supervisorScope {
                val whitelistNames = appSettings.suggestionSourcesWhitelist     
                if (whitelistNames.isEmpty()) return@supervisorScope emptyList()

                // Check Singleton Memory Cache
                if (!forceRefresh && forceGenreTag == null && !memoryCache.isNullOrEmpty() && 
                    cachedWhitelist == whitelistNames && cachedSortOrder == listSortOrder) {
                        return@supervisorScope memoryCache!!
                }

                // If parameters changed or forced refresh, we must fetch
                cachedWhitelist = whitelistNames
                cachedSortOrder = listSortOrder

                val allEnabled = sourcesRepository.getEnabledSources()
                val sourcesToUse = allEnabled.filter { it.name in whitelistNames }.shuffled().take(8)

                if (sourcesToUse.isEmpty()) return@supervisorScope emptyList()  

                val tagsBlacklist = TagsBlacklist(appSettings.suggestionsTagsBlacklist, 0.4f)

                val topTags = withContext(Dispatchers.IO) {
                        getUserAffinityTagsUseCase()
                }

                val queries = sourcesToUse.map { source ->
                        async(Dispatchers.IO) {
                                val computedSearchTag = if (forceGenreTag == null && topTags.isNotEmpty() && Math.random() > 0.5) {
                                        topTags.random() 
                                } else {
                                        forceGenreTag
                                }
                                val resultList = fetch(source, computedSearchTag, tagsBlacklist, listSortOrder)
                                Pair(resultList, computedSearchTag)
                        }
                }

                val results = queries.awaitAll()

                val mergedList = mutableListOf<MangaSuggestion>()
                val iterators = results.map { it.first.iterator() to it.second }

                var hasMore = true
                while (hasMore) {
                        hasMore = false
                        for ((it, tag) in iterators) {
                                for (i in 0 until 5) {
                                        if (it.hasNext()) {
                                                val manga = it.next()
                                                val isSpecificTag = tag != null && tag in topTags
                                                val reason = if (isSpecificTag) "Because you read $tag" else null
                                                mergedList.add(MangaSuggestion(manga, 1.0f, reason))
                                                hasMore = true
                                        }
                                }
                        }
                }

                val finalFeed = mergedList.distinctBy { it.manga.url to it.manga.source }

                // ── Affinity re-ranking ────────────────────────────────────────────────
                // Early-exit for new users: skip all scoring work when there are no tags.
                val sortedFeed = if (topTags.isNotEmpty() && forceGenreTag == null) {
                        withContext(Dispatchers.IO) {
                                // Build the tag set once for O(1) lookups inside the inner loop.
                                val topTagSet: Set<String> = topTags.toHashSet()

                                // Stable Scoring Wrapper: preserves originalIndex so ties in
                                // relevanceScore resolve deterministically by provider order
                                // (popularity/date), not by JVM sort instability.
                                data class ScoredSuggestion(
                                        val item: MangaSuggestion,
                                        val relevanceScore: Int,
                                        val originalIndex: Int,
                                )

                                finalFeed
                                        .mapIndexed { index, mangaSug ->
                                                // Null-safe: manga.tags may be null from some sources.
                                                val score = mangaSug.manga.tags
                                                        ?.count { tag -> tag.title.trim().lowercase() in topTagSet }
                                                        ?: 0
                                                ScoredSuggestion(mangaSug, score, index)
                                        }
                                        // Primary key: highest relevance first.
                                        // Secondary key: lower originalIndex first → stable tie-breaker.
                                        .sortedWith(
                                                compareByDescending<ScoredSuggestion> { it.relevanceScore }
                                                        .thenBy { it.originalIndex }
                                        )
                                        .map { it.item }
                        }
                } else {
                        finalFeed
                }

                if (forceGenreTag == null) {
                        memoryCache = sortedFeed
                }
                sortedFeed
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
                        
                        val order = if (forceGenreTag == null) {
                                if (targetPrimarySort in availableOrders) targetPrimarySort else availableOrders.firstOrNull()
                        } else {
                                listOf(SortOrder.POPULARITY, SortOrder.UPDATED).firstOrNull { it in availableOrders }
                                        ?: availableOrders.firstOrNull()
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
