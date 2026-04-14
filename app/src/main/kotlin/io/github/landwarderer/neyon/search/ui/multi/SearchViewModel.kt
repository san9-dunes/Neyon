package io.github.landwarderer.neyon.search.ui.multi

import androidx.collection.ArraySet
import androidx.collection.LongSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.model.LocalMangaSource
import io.github.landwarderer.neyon.core.model.UnknownMangaSource
import io.github.landwarderer.neyon.core.nav.AppRouter
import io.github.landwarderer.neyon.core.prefs.ListMode
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.util.ext.append
import io.github.landwarderer.neyon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.neyon.core.util.ext.toLocale
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import io.github.landwarderer.neyon.favourites.domain.FavouritesRepository
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.list.domain.MangaListMapper
import io.github.landwarderer.neyon.list.ui.model.ButtonFooter
import io.github.landwarderer.neyon.list.ui.model.EmptyState
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.LoadingFooter
import io.github.landwarderer.neyon.list.ui.model.LoadingState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import io.github.landwarderer.neyon.search.domain.SearchKind
import io.github.landwarderer.neyon.search.domain.SearchV2Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.plus
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaListMapper: MangaListMapper,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val query = savedStateHandle.get<String>(AppRouter.KEY_QUERY).orEmpty()
	val kind = savedStateHandle.get<SearchKind>(AppRouter.KEY_KIND) ?: SearchKind.SIMPLE

	private var includeDisabledSources = MutableStateFlow(false)
	private var pinnedOnly = MutableStateFlow(false)
	private var hideEmpty = MutableStateFlow(true)
	private val results = MutableStateFlow<List<SearchResultsListModel>>(emptyList())

	private var searchJob: Job? = null
	private val sourcesSemaphore = Semaphore(5)

	val list: StateFlow<List<ListModel>> = combine(
		results,
		isLoading.dropWhile { !it },
		includeDisabledSources,
		hideEmpty,
	) { list, loading, includeDisabled, hideEmptyVal ->
		val filteredList = if (hideEmptyVal) {
			list.filter { it.list.isNotEmpty() || it.error != null || it.loading }
		} else {
			list
		}
		when {
			filteredList.isEmpty() -> listOf(
				when {
					loading -> LoadingState
					else -> EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_search_holder_secondary,
						actionStringRes = 0,
					)
				},
			)

			loading -> filteredList + LoadingFooter()
			includeDisabled -> filteredList
			else -> filteredList + ButtonFooter(R.string.search_disabled_sources)
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		doSearch()
	}

	fun getItems(ids: LongSet): Set<Manga> {
		val snapshot = results.value
		val result = ArraySet<Manga>(ids.size)
		snapshot.forEach { x ->
			for (item in x.list) {
				if (item.id in ids) {
					result.add(item.manga)
				}
			}
		}
		return result
	}

	fun retry() {
		searchJob?.cancel()
		results.value = emptyList()
		includeDisabledSources.value = false
		doSearch()
	}

	fun setPinnedOnly(value: Boolean) {
		if (pinnedOnly.value != value) {
			pinnedOnly.value = value
			retry()
		}
	}

	fun setHideEmpty(value: Boolean) {
		hideEmpty.value = value
	}

	fun continueSearch() {
		if (includeDisabledSources.value) {
			return
		}
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.IO) {
			includeDisabledSources.value = true
			prevJob?.join()
			val sources = if (pinnedOnly.value) {
				emptyList()
			} else {
				sourcesRepository.getDisabledSources()
					.sortedByDescending { it.priority() }
			}
			
			// Map sources into UI State model with loading=true
			val newLoaders = sources.map { source ->
				SearchResultsListModel(0, source, null, null, emptyList(), null, true)
			}
			results.update { it + newLoaders }

			sources.map { source ->
				launch(Dispatchers.IO) {
					sourcesSemaphore.acquire()
					try {
						val result = searchSource(source)
						updateResult(0, source, result)
					} finally {
						sourcesSemaphore.release()
					}
				}
			}.joinAll()
		}
	}

	private fun doSearch() {
		val prevJob = searchJob
		prevJob?.cancel()
		searchJob = launchLoadingJob(Dispatchers.IO) {
			prevJob?.join()
			val sources = if (pinnedOnly.value) {
				sourcesRepository.getPinnedSources().toList()
			} else {
				sourcesRepository.getEnabledSources()
			}
			
			// Map targets into UI State model with Loading indicator
			val initialLoaders = mutableListOf<SearchResultsListModel>()
			initialLoaders.add(SearchResultsListModel(R.string.history, UnknownMangaSource, null, null, emptyList(), null, true))
			initialLoaders.add(SearchResultsListModel(R.string.favourites, UnknownMangaSource, null, null, emptyList(), null, true))
			initialLoaders.add(SearchResultsListModel(0, LocalMangaSource, null, null, emptyList(), null, true))
			sources.forEach { source ->
				initialLoaders.add(SearchResultsListModel(0, source, null, null, emptyList(), null, true))
			}
			results.value = initialLoaders

			val jobs = mutableListOf<Job>()
			jobs.add(launch(Dispatchers.IO) { updateResult(R.string.history, UnknownMangaSource, searchHistory()) })
			jobs.add(launch(Dispatchers.IO) { updateResult(R.string.favourites, UnknownMangaSource, searchFavorites()) })
			jobs.add(launch(Dispatchers.IO) { updateResult(0, LocalMangaSource, searchLocal()) })

			sources.forEach { source ->
				jobs.add(launch(Dispatchers.IO) {
					sourcesSemaphore.acquire()
					try {
						val result = searchSource(source)
						updateResult(0, source, result)
					} finally {
						sourcesSemaphore.release()
					}
				})
			}
			jobs.joinAll()
		}
	}

	// impl

	private suspend fun searchSource(source: MangaSource): SearchResultsListModel? = runCatchingCancellable {
		withTimeoutOrNull(15000L) {
			val searchHelper = searchHelperFactory.create(source)
			searchHelper(query, kind)
		}
	}.fold(
		onSuccess = { result ->
			if (result == null || result.manga.isEmpty()) {
				null
			} else {
				val list = mangaListMapper.toListModelList(
					manga = result.manga,
					mode = ListMode.GRID,
				)
				SearchResultsListModel(
					titleResId = 0,
					source = source,
					list = list,
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
				)
			}
		},
		onFailure = { error ->
			error.printStackTraceDebug("SearchViewModel::searchSource", source.toString())
			if (source is MangaParserSource && source.isBroken) {
				null
			} else {
				SearchResultsListModel(0, source, null, null, emptyList(), error)
			}
		},
	)

	private suspend fun searchHistory(): SearchResultsListModel? = runCatchingCancellable {
		historyRepository.search(query, kind, Int.MAX_VALUE)
	}.fold(
		onSuccess = { result ->
			if (result.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.history,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(manga = result, mode = ListMode.GRID),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.history,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchFavorites(): SearchResultsListModel? = runCatchingCancellable {
		favouritesRepository.search(query, kind, Int.MAX_VALUE)
	}.fold(
		onSuccess = { result ->
			if (result.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.favourites,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(
						manga = result,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_FAVORITE,
					),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.favourites,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchLocal(): SearchResultsListModel? = runCatchingCancellable {
		searchHelperFactory.create(LocalMangaSource).invoke(query, kind)
	}.fold(
		onSuccess = { result ->
			if (!result?.manga.isNullOrEmpty()) {
				SearchResultsListModel(
					titleResId = 0,
					source = LocalMangaSource,
					list = mangaListMapper.toListModelList(
						manga = result.manga,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_SAVED,
					),
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = 0,
				source = LocalMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private fun updateResult(titleResId: Int, source: MangaSource, result: SearchResultsListModel?) {
		results.update { currentList ->
			currentList.mapNotNull { model ->
				if (model.titleResId == titleResId && model.source == source) {
					result?.copy(loading = false)
				} else {
					model
				}
			}
		}
	}

	private fun MangaSource.priority(): Int {
		var res = 0
		if (this is MangaParserSource) {
			if (locale.toLocale() == Locale.getDefault()) res += 2
		}
		return res
	}
}
