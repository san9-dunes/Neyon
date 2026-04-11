package io.github.landwarderer.futon.suggestions.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.model.distinctById
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.util.ext.asArrayList
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

	suspend fun mixFeed(): List<Manga> = supervisorScope {
		val whitelistNames = appSettings.suggestionSourcesWhitelist
		if (whitelistNames.isEmpty()) return@supervisorScope emptyList()

		// 1. Get enabled sources that are whitelisted
		val allEnabled = sourcesRepository.getEnabledSources()
		val sourcesToUse = allEnabled.filter { it.name in whitelistNames }

		if (sourcesToUse.isEmpty()) return@supervisorScope emptyList()

		// 2. Extract Top Genres from History (Top 10, pick 3)
		val seedHistory = historyRepository.getList(0, 50).distinctById()
		val topTags = seedHistory.flatMap { it.tags.map { x -> x.title } }
			.groupingBy { it }
			.eachCount()
			.entries
			.sortedByDescending { it.value }
			.take(10)
			.map { it.key }
			.shuffled()
			.take(3)

		// 3. Concurrently fetch arrays
		val genreQueries = sourcesToUse.map { async(Dispatchers.IO) { fetch(it, true, topTags) } }
		val latestQueries = sourcesToUse.map { async(Dispatchers.IO) { fetch(it, false, topTags) } }

		val genreLists = genreQueries.awaitAll()
		val latestLists = latestQueries.awaitAll()

		val allGenres = genreLists.flatten().shuffled().iterator()
		val allLatest = latestLists.flatten().shuffled().iterator()

		val mergedList = mutableListOf<Manga>()

		// 4. Weighted Interleave Algorithm (2 Genre Match blocks to 1 Latest)
		while (allGenres.hasNext() || allLatest.hasNext()) {
			if (allGenres.hasNext()) mergedList.add(allGenres.next())
			if (allGenres.hasNext()) mergedList.add(allGenres.next())
			if (allLatest.hasNext()) mergedList.add(allLatest.next())
		}
		
		mergedList.distinctById()
	}

	private suspend fun fetch(source: MangaSource, isGenreMatch: Boolean, topTags: List<String>): List<Manga> = runCatchingCancellable {
		withTimeoutOrNull(5000L) {
			val repository = mangaRepositoryFactory.create(source)
			val availableOrders = repository.sortOrders
			val order = preferredSortOrders.firstOrNull { it in availableOrders } ?: availableOrders.firstOrNull()
			
			val filter = if (isGenreMatch && topTags.isNotEmpty()) {
				val targetTagTitle = topTags.randomOrNull()
				val matchedTag = targetTagTitle?.let { title ->
					repository.getFilterOptions().availableTags.find { x -> x.title.almostEquals(title, 0.7f) }
				}
				if (matchedTag != null) {
					MangaListFilter(tags = setOf(matchedTag))
				} else {
					MangaListFilter(query = targetTagTitle) // Fallback to keyword search
				}
			} else {
				MangaListFilter() // Latest / Default Popular
			}

			val list = repository.getList(
				offset = 0,
				order = order,
				filter = filter
			).asArrayList()

			if (appSettings.isSuggestionsExcludeNsfw) {
				list.removeAll { it.isNsfw() }
			}
			list.shuffle()
			list.take(15) // Limit each block so we don't blow up memory
		} ?: emptyList()
	}.getOrDefault(emptyList())
}
