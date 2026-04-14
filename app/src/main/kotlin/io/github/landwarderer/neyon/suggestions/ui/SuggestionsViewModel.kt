package io.github.landwarderer.neyon.suggestions.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.koitharu.kotatsu.parsers.model.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.parser.MangaDataRepository
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.observeAsFlow
import io.github.landwarderer.neyon.core.util.ext.onFirst
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import io.github.landwarderer.neyon.list.domain.MangaListMapper
import io.github.landwarderer.neyon.list.domain.QuickFilterListener
import io.github.landwarderer.neyon.list.ui.MangaListViewModel
import io.github.landwarderer.neyon.list.ui.model.ListHeader
import io.github.landwarderer.neyon.suggestions.domain.MangaSuggestion
import io.github.landwarderer.neyon.list.ui.model.EmptyState
import io.github.landwarderer.neyon.list.ui.model.LoadingState
import io.github.landwarderer.neyon.list.ui.model.toErrorState
import io.github.landwarderer.neyon.suggestions.domain.FeedAggregator
import io.github.landwarderer.neyon.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject
import io.github.landwarderer.neyon.local.data.LocalStorageChanges
import io.github.landwarderer.neyon.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.flow
import io.github.landwarderer.neyon.list.domain.ListSortOrder

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
	private val feedAggregator: FeedAggregator,
	private val settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: SuggestionsListQuickFilter,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_SUGGESTIONS) { suggestionsListMode }
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, settings.suggestionsListMode)

	private val refreshSignal = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(false) }
	// Holds an optional tag override from chip clicks — null means use history-derived tags
	private val genreOverride = MutableSharedFlow<String?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(null) }
	private var lastMenuRefresh = 0L

	override val content = combine(
		combine(
			refreshSignal,
			genreOverride,
			settings.observeAsFlow(AppSettings.KEY_SUGGESTIONS_ORDER) { suggestionsSortOrder }
		) { forceRefresh, genre, sortOrder -> Triple(forceRefresh, genre, sortOrder) }
		.flatMapLatest { (forceRefresh, genre, sortOrder) ->
				flow<List<MangaSuggestion>?> {
						kotlinx.coroutines.withContext(Dispatchers.Main) { loadingCounter.increment() }
						// Only wipe the screen if explicitly requested a fresh fetch
						if (forceRefresh || genre != null) {
								emit(null)
						}
						val effectiveSortOrder = if (genre != null) ListSortOrder.POPULARITY else sortOrder
						emit(feedAggregator.mixFeed(genre, forceRefresh, effectiveSortOrder))
						kotlinx.coroutines.withContext(Dispatchers.Main) { loadingCounter.decrement() }
				}
		},
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
		settings.observeAsFlow(AppSettings.KEY_SUGGESTION_SOURCES_WHITELIST) { suggestionSourcesWhitelist },
	) { list, filters, mode, whitelist ->
		when {
			whitelist.isEmpty() -> listOf(
				EmptyState(
					icon = R.drawable.ic_empty_common,
					textPrimary = R.string.no_manga_sources,
					textSecondary = R.string.no_manga_sources_text,
					actionStringRes = R.string.suggestions_manage_sources,
				),
			)
			list == null -> listOfNotNull(
				quickFilter.filterItem(filters),
				LoadingState
			)
			list.isEmpty() -> if (filters.isEmpty()) {
				listOf(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_suggestion_holder,
						actionStringRes = R.string.suggestions_manage_sources,
					),
				)
			} else {
				listOfNotNull(
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					),
				)
			}

			else -> buildList(list.size + 5) {
				quickFilter.filterItem(filters)?.let(::add)
				val grouped = list.groupBy { it.reason }
				for ((reason, items) in grouped) {
					if (reason != null) {
						add(ListHeader(reason))
					}
					mangaListMapper.toListModelList(this, items.map { it.manga }, mode)
				}
			}
		}
	}.catch {
                kotlinx.coroutines.withContext(Dispatchers.Main) { loadingCounter.decrement() }
                emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	// Wire swipe-to-refresh: no rate limit — user explicitly pulled
	override fun onRefresh() {
		refreshSignal.tryEmit(true)
	}

	override fun onRetry() {
		updateSuggestions()
	}

	// Toolbar refresh button: 3-second guard against rapid taps
	fun updateSuggestions() {
		val now = System.currentTimeMillis()
		if (now - lastMenuRefresh < 3000L) return
		lastMenuRefresh = now
		genreOverride.tryEmit(null) // Reset genre override
		refreshSignal.tryEmit(true)
	}

	// Called by genre chip tap — triggers live re-fetch with that genre forced
	override fun toggleFilterOption(option: ListFilterOption) {
		quickFilter.toggleFilterOption(option)
		if (option is ListFilterOption.Tag) {
			// Check if the tag is now applied or cleared
			val applied = quickFilter.appliedOptions.value
			val newOverride = if (option in applied) option.tag.title else null
			genreOverride.tryEmit(newOverride)
			refreshSignal.tryEmit(true)
		}
	}

	override fun removeFilterOption(option: ListFilterOption) {
		quickFilter.removeFilterOption(option)
		if (option is ListFilterOption.Tag) {
			val applied = quickFilter.appliedOptions.value
			val newOverride = if (applied.filterIsInstance<ListFilterOption.Tag>().isNotEmpty()) applied.filterIsInstance<ListFilterOption.Tag>().first().tag.title else null
			if (genreOverride.replayCache.firstOrNull() == option.tag.title) {
				genreOverride.tryEmit(newOverride)
				refreshSignal.tryEmit(true)
			}
		}
	}
}

