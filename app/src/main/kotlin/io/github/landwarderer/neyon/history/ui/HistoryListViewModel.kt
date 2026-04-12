package io.github.landwarderer.neyon.history.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.model.MangaHistory
import io.github.landwarderer.neyon.core.model.isLocal
import io.github.landwarderer.neyon.core.parser.MangaDataRepository
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.ListMode
import io.github.landwarderer.neyon.core.prefs.observeAsFlow
import io.github.landwarderer.neyon.core.prefs.observeAsStateFlow
import io.github.landwarderer.neyon.core.ui.util.ReversibleAction
import io.github.landwarderer.neyon.core.util.ext.calculateTimeAgo
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.core.util.ext.flattenLatest
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.history.domain.HistoryListQuickFilter
import io.github.landwarderer.neyon.history.domain.MarkAsReadUseCase
import io.github.landwarderer.neyon.history.domain.model.MangaWithHistory
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import io.github.landwarderer.neyon.list.domain.ListSortOrder
import io.github.landwarderer.neyon.list.domain.MangaListMapper
import io.github.landwarderer.neyon.list.domain.QuickFilterListener
import io.github.landwarderer.neyon.list.domain.ReadingProgress
import io.github.landwarderer.neyon.list.ui.MangaListViewModel
import io.github.landwarderer.neyon.list.ui.model.EmptyState
import io.github.landwarderer.neyon.list.ui.model.InfoModel
import io.github.landwarderer.neyon.list.ui.model.ListHeader
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.LoadingState
import io.github.landwarderer.neyon.list.ui.model.toErrorState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.mapToSet
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import io.github.landwarderer.neyon.local.data.LocalStorageChanges
import io.github.landwarderer.neyon.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import io.github.landwarderer.neyon.alternatives.domain.AlternativesUseCase
import io.github.landwarderer.neyon.alternatives.domain.MigrateUseCase
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.model.getTitle
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository

private data class ContentInput(
	val filters: Set<ListFilterOption>,
	val list: List<MangaWithHistory>,
	val grouped: Boolean,
	val mode: ListMode,
	val isIncognito: Boolean,
)

private const val PAGE_SIZE = 16

@HiltViewModel
class HistoryListViewModel @Inject constructor(
	private val repository: HistoryRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	private val quickFilter: HistoryListQuickFilter,
	mangaDataRepository: MangaDataRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val alternativesUseCase: AlternativesUseCase,
	private val migrateUseCase: MigrateUseCase,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	private val sortOrder: StateFlow<ListSortOrder> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_HISTORY_ORDER,
		valueProducer = { historySortOrder },
	)

	override val listMode = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_LIST_MODE_HISTORY,
		valueProducer = { historyListMode },
	)

	private val isGroupingEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_HISTORY_GROUPING,
		valueProducer = { isHistoryGroupingEnabled },
	).combine(sortOrder) { g, s ->
		g && s.isGroupingSupported()
	}

	private val limit = MutableStateFlow(PAGE_SIZE)
	private val isPaginationReady = AtomicBoolean(false)

	val isStatsEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_STATS_ENABLED,
		valueProducer = { settings.isStatsEnabled },
	)

	override val content = combine(
		quickFilter.appliedOptions,
		observeHistory(),
		isGroupingEnabled,
		observeListModeWithTriggers(),
		settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE) { isIncognitoModeEnabled },
	) { filters, list, grouped, mode, incognito ->
		ContentInput(filters, list, grouped, mode, incognito)
	}.combine(
		sourcesRepository.observeEnabledSourcesCount(),
	) { input, _ ->
		mapList(input.list, input.grouped, input.mode, input.filters, input.isIncognito)
	}.distinctUntilChanged().onEach {
		isPaginationReady.set(true)
	}.catch { e ->
		emit(listOf(e.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun clearHistory(minDate: Instant?) {
		launchJob(Dispatchers.IO) {
			val stringRes = if (minDate == null) {
				repository.clear()
				R.string.history_cleared
			} else {
				repository.deleteAfter(minDate.toEpochMilli())
				R.string.removed_from_history
			}
			onActionDone.call(ReversibleAction(stringRes, null))
		}
	}

	fun removeNotFavorite() {
		launchJob(Dispatchers.IO) {
			repository.deleteNotFavorite()
			onActionDone.call(ReversibleAction(R.string.removed_from_history, null))
		}
	}

	fun removeFromHistory(ids: Set<Long>) {
		if (ids.isEmpty()) {
			return
		}
		launchJob(Dispatchers.IO) {
			val handle = repository.delete(ids)
			onActionDone.call(ReversibleAction(R.string.removed_from_history, handle))
		}
	}

	fun markAsRead(items: Set<Manga>) {
		launchLoadingJob(Dispatchers.IO) {
			markAsReadUseCase(items)
		}
	}

	fun requestMoreItems() {
		if (isPaginationReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	val fastMigrationCandidates = MutableEventFlow<Pair<Manga, List<Manga>>>()
	private val isMigrating = AtomicBoolean(false)

	fun performFastMigration(manga: Manga) {
		if (!isMigrating.compareAndSet(false, true)) return
		launchLoadingJob(Dispatchers.IO) {
			try {
				val candidates = alternativesUseCase(manga, throughDisabledSources = false)
					.take(3)
					.toList()
				if (candidates.isNotEmpty()) {
					fastMigrationCandidates.call(manga to candidates)
				} else {
					onActionDone.call(ReversibleAction(R.string.nothing_found, null))
				}
			} finally {
				isMigrating.set(false)
			}
		}
	}

	fun confirmFastMigration(oldManga: Manga, newManga: Manga) {
		if (!isMigrating.compareAndSet(false, true)) return
		launchLoadingJob(Dispatchers.IO) {
			try {
				migrateUseCase(oldManga, newManga)
				onActionDone.call(ReversibleAction(R.string.migration_completed, null))
			} finally {
				isMigrating.set(false)
			}
		}
	}



	private fun observeHistory() = combine(
		sortOrder,
		quickFilter.appliedOptions.combineWithSettings(),
		limit,
	) { order, filters, limit ->
		isPaginationReady.set(false)
		repository.observeAllWithHistory(order, filters, limit)
	}.flattenLatest()

	private suspend fun mapList(
		list: List<MangaWithHistory>,
		grouped: Boolean,
		mode: ListMode,
		filters: Set<ListFilterOption>,
		isIncognito: Boolean,
	): List<ListModel> {
		if (list.isEmpty()) {
			return if (filters.isEmpty()) {
				listOf(getEmptyState(hasFilters = false))
			} else {
				listOfNotNull(quickFilter.filterItem(filters), getEmptyState(hasFilters = true))
			}
		}
		val result = ArrayList<ListModel>((if (grouped) (list.size * 1.4).toInt() else list.size) + 2)
		quickFilter.filterItem(filters)?.let(result::add)
		if (isIncognito) {
			result += InfoModel(
				key = AppSettings.KEY_INCOGNITO_MODE,
				title = R.string.incognito_mode,
				text = R.string.incognito_mode_hint,
				icon = R.drawable.ic_incognito,
			)
		}
		val order = sortOrder.value
		var prevHeader: ListHeader? = null
		var isEmpty = true
		for ((manga, history) in list) {
			isEmpty = false
			if (grouped) {
				val header = history.header(order)
				if (header != prevHeader) {
					if (header != null) {
						result += header
					}
					prevHeader = header
				}
			}
			result += mangaListMapper.toListModel(manga, mode)
		}
		if (filters.isNotEmpty() && isEmpty) {
			result += getEmptyState(hasFilters = true)
		}
		return result
	}

	private fun MangaHistory.header(order: ListSortOrder): ListHeader? = when (order) {
		ListSortOrder.LAST_READ,
		ListSortOrder.LONG_AGO_READ -> calculateTimeAgo(updatedAt)?.let {
			ListHeader(it)
		} ?: ListHeader(R.string.unknown)

		ListSortOrder.OLDEST,
		ListSortOrder.NEWEST -> calculateTimeAgo(createdAt)?.let {
			ListHeader(it)
		} ?: ListHeader(R.string.unknown)

		ListSortOrder.UNREAD,
		ListSortOrder.PROGRESS -> ListHeader(
			when {
				ReadingProgress.isCompleted(percent) -> R.string.status_completed
				percent in 0f..0.01f -> R.string.status_planned
				percent in 0f..1f -> R.string.status_reading
				else -> R.string.unknown
			},
		)

		ListSortOrder.ALPHABETIC,
		ListSortOrder.ALPHABETIC_REVERSE,
		ListSortOrder.POPULARITY,
		ListSortOrder.RELEVANCE,
		ListSortOrder.NEW_CHAPTERS,
		ListSortOrder.UPDATED,
		ListSortOrder.RATING -> null
	}

	private fun getEmptyState(hasFilters: Boolean) = if (hasFilters) {
		EmptyState(
			icon = R.drawable.ic_empty_history,
			textPrimary = R.string.nothing_found,
			textSecondary = R.string.text_empty_holder_secondary_filtered,
			actionStringRes = R.string.reset_filter,
		)
	} else {
		EmptyState(
			icon = R.drawable.ic_empty_history,
			textPrimary = R.string.text_history_holder_primary,
			textSecondary = R.string.text_history_holder_secondary,
			actionStringRes = 0,
		)
	}
}
