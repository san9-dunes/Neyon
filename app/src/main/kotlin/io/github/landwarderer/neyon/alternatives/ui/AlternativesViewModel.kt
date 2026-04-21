package io.github.landwarderer.neyon.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.alternatives.domain.AlternativesUseCase
import io.github.landwarderer.neyon.alternatives.domain.MigrateUseCase
import io.github.landwarderer.neyon.core.model.chaptersCount
import io.github.landwarderer.neyon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.neyon.core.nav.AppRouter
import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.prefs.ListMode
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.core.util.ext.require
import io.github.landwarderer.neyon.list.domain.MangaListMapper
import io.github.landwarderer.neyon.list.ui.model.ButtonFooter
import io.github.landwarderer.neyon.list.ui.model.EmptyState
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.LoadingFooter
import io.github.landwarderer.neyon.list.ui.model.LoadingState
import io.github.landwarderer.neyon.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrDefault
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject

private const val PARALLEL_SEARCH_LIMIT = 5

@HiltViewModel
class AlternativesViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val alternativesUseCase: AlternativesUseCase,
	private val migrateUseCase: MigrateUseCase,
	private val mangaListMapper: MangaListMapper,
) : BaseViewModel() {

	val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	private var includeDisabledSources = MutableStateFlow(false)

	/** Per-source search results, analogous to SearchViewModel.results. */
	private val sourceResults = MutableStateFlow<List<AlternativeSourceModel>>(emptyList())

	private var migrationJob: Job? = null
	private var searchJob: Job? = null

	private val mangaDetails = suspendLazy {
		mangaRepositoryFactory.create(manga.source).getDetails(manga)
	}

	val onMigrated = MutableEventFlow<Manga>()

	val list: StateFlow<List<ListModel>> = combine(
		sourceResults,
		isLoading,
		includeDisabledSources,
	) { results, loading, includeDisabled ->
		// Filter out sources that finished with no results and no error
		val visibleResults = results.filter { it.loading || it.items.isNotEmpty() || it.error != null }
		when {
			visibleResults.isEmpty() -> listOf(
				when {
					loading -> LoadingState
					else -> EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_search_holder_secondary,
						actionStringRes = R.string.find_similar,
					)
				},
			)

			loading -> visibleResults + LoadingFooter()
			includeDisabled -> visibleResults
			else -> visibleResults + ButtonFooter(R.string.search_disabled_sources)
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		doSearch(throughDisabledSources = false)
	}

	fun retry() {
		searchJob?.cancel()
		sourceResults.value = emptyList()
		includeDisabledSources.value = false
		doSearch(throughDisabledSources = false)
	}

	fun continueSearch() {
		if (includeDisabledSources.value) return
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.IO) {
			includeDisabledSources.value = true
			prevJob?.join()
			val disabledSources = alternativesUseCase.getSources(manga.source, throughDisabledSources = true)
				.filter { source -> sourceResults.value.none { it.source == source } }
			searchSources(disabledSources)
		}
	}

	fun migrate(target: Manga) {
		if (migrationJob?.isActive == true) return
		migrationJob = launchLoadingJob(Dispatchers.IO) {
			migrateUseCase(manga, target)
			onMigrated.call(target)
		}
	}

	private fun doSearch(throughDisabledSources: Boolean) {
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.IO) {
			prevJob?.cancelAndJoin()
			sourceResults.value = emptyList()

			val ref = mangaDetails.getOrDefault(manga)
			val sources = alternativesUseCase.getSources(ref.source, throughDisabledSources)

			// Pre-populate per-source loading states (like SearchViewModel does)
			sourceResults.value = sources.map { source ->
				AlternativeSourceModel(source = source, items = emptyList(), error = null, loading = true)
			}
			searchSources(sources, refManga = ref)
		}
	}

	/**
	 * Searches each source in [sources] with up to [PARALLEL_SEARCH_LIMIT] concurrent requests,
	 * updating [sourceResults] per-source as each completes — identical to SearchViewModel's approach.
	 */
	private suspend fun searchSources(
		sources: List<MangaSource>,
		refManga: Manga? = null,
	) {
		val actualRef = refManga ?: mangaDetails.getOrDefault(manga)
		val cleanTitle = alternativesUseCase.cleanTitle(actualRef.title) ?: return
		val refChapters = actualRef.chaptersCount()
		val semaphore = Semaphore(PARALLEL_SEARCH_LIMIT)

		kotlinx.coroutines.coroutineScope {
			sources.map { source ->
				launch(Dispatchers.IO) {
					semaphore.withPermit {
						val result = alternativesUseCase.searchSource(source, cleanTitle, actualRef.id)
						updateSourceResult(source, result, refChapters)
					}
				}
			}.joinAll()
		}
	}

	private suspend fun updateSourceResult(
		source: MangaSource,
		result: Result<List<Manga>>,
		refChapters: Int,
	) {
		val successItems = result.getOrNull()?.map { m ->
			MangaAlternativeModel(
				mangaModel = mangaListMapper.toListModel(m, ListMode.GRID) as MangaGridModel,
				referenceChapters = refChapters,
			)
		}
		val error = result.exceptionOrNull()

		sourceResults.update { current ->
			current.map { model ->
				if (model.source != source) return@map model
				if (successItems != null) {
					model.copy(items = successItems, error = null, loading = false)
				} else {
					model.copy(items = emptyList(), error = error, loading = false)
				}
			}
		}
	}
}
