package io.github.landwarderer.neyon.details.ui.pager

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import okio.FileNotFoundException
import io.github.landwarderer.neyon.bookmarks.domain.BookmarksRepository
import io.github.landwarderer.neyon.core.model.toChipModel
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.observeAsStateFlow
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.ui.util.ReversibleAction
import io.github.landwarderer.neyon.core.util.LocaleStringComparator
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.core.util.ext.combine
import io.github.landwarderer.neyon.core.util.ext.isEmpty
import io.github.landwarderer.neyon.core.util.ext.requireValue
import io.github.landwarderer.neyon.core.util.ext.sortedWithSafe
import io.github.landwarderer.neyon.details.data.MangaDetails
import io.github.landwarderer.neyon.details.domain.DetailsInteractor
import io.github.landwarderer.neyon.details.ui.DetailsActivity
import io.github.landwarderer.neyon.details.ui.DetailsViewModel
import io.github.landwarderer.neyon.details.ui.mapChapters
import io.github.landwarderer.neyon.details.ui.model.ChapterListItem
import io.github.landwarderer.neyon.download.ui.worker.DownloadTask
import io.github.landwarderer.neyon.download.domain.DownloadState
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import io.github.landwarderer.neyon.download.ui.worker.DownloadWorker
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import io.github.landwarderer.neyon.local.domain.DeleteLocalMangaUseCase
import io.github.landwarderer.neyon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaState
import io.github.landwarderer.neyon.reader.ui.ReaderActivity
import io.github.landwarderer.neyon.reader.ui.ReaderState
import io.github.landwarderer.neyon.reader.ui.ReaderViewModel

abstract class ChaptersPagesViewModel(
	@JvmField protected val settings: AppSettings,
	@JvmField protected val interactor: DetailsInteractor,
	private val bookmarksRepository: BookmarksRepository,
	private val historyRepository: HistoryRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val localStorageChanges: SharedFlow<LocalManga?>,
) : BaseViewModel() {

	val mangaDetails = MutableStateFlow<MangaDetails?>(null)
	val readingState = MutableStateFlow<ReaderState?>(null)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onDownloadStarted = MutableEventFlow<Unit>()
	val onMangaRemoved = MutableEventFlow<Manga>()

	private val chaptersQuery = MutableStateFlow("")
	val selectedBranch = MutableStateFlow<String?>(null)

	val manga = mangaDetails.map { x -> x?.toManga() }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val coverUrl = mangaDetails.map { x -> x?.coverUrl }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val isChaptersReversed = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_REVERSE_CHAPTERS,
		valueProducer = { isChaptersReverse },
	)

	val isChaptersInGridView = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_GRID_VIEW_CHAPTERS,
		valueProducer = { isChaptersGridView },
	)

	val isDownloadedOnly = MutableStateFlow(false)

	val newChaptersCount = mangaDetails.flatMapLatest { d ->
		if (d?.isLocal == false) {
			interactor.observeNewChapters(d.id)
		} else {
			flowOf(0)
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, 0)

	val emptyReason: StateFlow<EmptyMangaReason?> = combine(
		mangaDetails,
		isLoading,
		onError.onStart { emit(null) },
	) { details, loading, error ->
		when {
			details == null || loading -> null
			details.chapters.isNotEmpty() -> null
			details.toManga().state == MangaState.RESTRICTED -> EmptyMangaReason.RESTRICTED
			error != null -> EmptyMangaReason.LOADING_ERROR
			else -> EmptyMangaReason.NO_CHAPTERS
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(), null)

	val bookmarks = mangaDetails.flatMapLatest {
		if (it != null) {
			bookmarksRepository.observeBookmarks(it.toManga()).withErrorHandling()
		} else {
			flowOf(emptyList())
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, emptyList())

	val downloadStatesFlow = downloadScheduler.observeWorks().combine(mangaDetails) { works, details ->
		if (details == null) return@combine emptyMap()
		val result = mutableMapOf<Long, Pair<Float, Boolean>>()
		for (work in works) {
			val workData = work.outputData.takeUnless { it.isEmpty }
				?: work.progress.takeUnless { it.isEmpty }
				?: continue
			val mangaId = DownloadState.getMangaId(workData)
			if (mangaId != details.id) continue
			
			val currentChapterId = DownloadState.getCurrentChapterId(workData)
			val max = DownloadState.getMax(workData)
			val progress = DownloadState.getProgress(workData)
			val isPaused = DownloadState.isPaused(workData)
			
			if (currentChapterId != 0L) {
				val percent = if (max > 0) progress.toFloat() / max else 0f
				result[currentChapterId] = percent to isPaused
			}
		}
		result
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, emptyMap())

	val chapters = combine(
		combine(
			mangaDetails,
			readingState.map { it?.chapterId ?: 0L }.distinctUntilChanged(),
			selectedBranch,
			newChaptersCount,
			bookmarks,
			isChaptersInGridView,
			isDownloadedOnly,
		) { manga, currentChapterId, branch, news, bookmarks, grid, downloadedOnly ->
			manga?.mapChapters(
				currentChapterId = currentChapterId,
				newCount = news,
				branch = branch,
				bookmarks = bookmarks,
				isGrid = grid,
				isDownloadedOnly = downloadedOnly,
				downloadStates = downloadStatesFlow.value,
			).orEmpty()
		},
		isChaptersReversed,
		chaptersQuery,
		downloadStatesFlow,
	) { list, reversed, query, states ->
		val processedList = if (states.isNotEmpty()) {
			list.map { item ->
				val state = states[item.chapter.id]
				if (state != null) {
					item.copy(downloadPercent = state.first, isDownloadPaused = state.second)
				} else {
					item.copy(downloadPercent = null, isDownloadPaused = false)
				}
			}
		} else list
		(if (reversed) processedList.asReversed() else processedList).filterSearch(query)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	val quickFilter = combine(
		mangaDetails,
		selectedBranch,
	) { details, branch ->
		val branches = details?.chapters?.toList()?.sortedWithSafe(
			compareBy(LocaleStringComparator()) { it.first },
		).orEmpty()
		if (branches.size > 1) {
			branches.map {
				val option = ListFilterOption.Branch(titleText = it.first, chaptersCount = it.second.size)
				option.toChipModel(isChecked = it.first == branch)
			}
		} else {
			emptyList()
		}
	}

	init {
		launchJob(Dispatchers.IO) {
			localStorageChanges
				.collect { onDownloadComplete(it) }
		}
	}

	fun setChaptersReversed(newValue: Boolean) {
		settings.isChaptersReverse = newValue
	}

	fun setChaptersInGridView(newValue: Boolean) {
		settings.isChaptersGridView = newValue
	}

	fun setSelectedBranch(branch: String?) {
		selectedBranch.value = branch
	}

	fun performChapterSearch(query: String?) {
		chaptersQuery.value = query?.trim().orEmpty()
	}

	fun getMangaOrNull(): Manga? = mangaDetails.value?.toManga()

	fun requireManga() = mangaDetails.requireValue().toManga()

	fun markChapterAsCurrent(chapterId: Long) {
		launchJob(Dispatchers.IO) {
			val manga = mangaDetails.requireValue()
			val chapters = checkNotNull(manga.chapters[selectedBranch.value])
			val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
			check(chapterIndex in chapters.indices) { "Chapter not found" }
			val percent = chapterIndex / chapters.size.toFloat()
			historyRepository.addOrUpdate(
				manga = manga.toManga(),
				chapterId = chapterId,
				page = 0,
				scroll = 0,
				percent = percent,
				force = true,
			)
		}
	}

	fun download(chaptersIds: Set<Long>?, allowMeteredNetwork: Boolean) {
		launchJob(Dispatchers.IO) {
			val manga = requireManga()
			val task = DownloadTask(
				mangaId = manga.id,
				isPaused = false,
				isSilent = false,
				chaptersIds = chaptersIds?.toLongArray(),
				destination = null,
				format = null,
				allowMeteredNetwork = allowMeteredNetwork,
			)
			downloadScheduler.schedule(setOf(manga to task))
			onDownloadStarted.call(Unit)
		}
	}

	fun pauseOrCancelDownload(chapterId: Long) {
		launchJob(Dispatchers.IO) {
			// For simplicity, we just skip or pause the chapter.
			val mangaId = mangaDetails.value?.id ?: return@launchJob
			val works = downloadScheduler.observeWorks().firstOrNull() ?: emptyList()
			for (work in works) {
				val data = work.outputData.takeUnless { it.isEmpty }
					?: work.progress.takeUnless { it.isEmpty }
					?: continue
				val currentMangaId = DownloadState.getMangaId(data)
				if (currentMangaId == mangaId) {
					val currentChapterId = DownloadState.getCurrentChapterId(data)
					if (currentChapterId == chapterId) {
						downloadScheduler.pause(work.id)
					}
				}
			}
		}
	}

	fun deleteLocalChapter(chapterId: Long) {
		val m = mangaDetails.value?.local?.manga
		if (m == null) {
			errorEvent.call(FileNotFoundException())
			return
		}
		launchLoadingJob(Dispatchers.IO) {
			io.github.landwarderer.neyon.local.data.output.LocalMangaUtil(m).deleteChapters(setOf(chapterId))
			onDownloadComplete(LocalManga(m))
		}
	}

	fun deleteLocal() {
		val m = mangaDetails.value?.local?.manga
		if (m == null) {
			errorEvent.call(FileNotFoundException())
			return
		}
		launchLoadingJob(Dispatchers.IO) {
			deleteLocalMangaUseCase(m)
			onMangaRemoved.call(m)
		}
	}

	private fun List<ChapterListItem>.filterSearch(query: String): List<ChapterListItem> {
		if (query.isEmpty() || this.isEmpty()) {
			return this
		}
		return filter { it.contains(query) }
	}

	private suspend fun onDownloadComplete(downloadedManga: LocalManga?) {
		downloadedManga ?: return
		mangaDetails.update {
			interactor.updateLocal(it, downloadedManga)
		}
	}

	class ActivityVMLazy(
		private val fragment: Fragment,
	) : Lazy<ChaptersPagesViewModel> {
		private var cached: ChaptersPagesViewModel? = null

		override val value: ChaptersPagesViewModel
			get() {
				val viewModel = cached
				return if (viewModel == null) {
					val activity = fragment.requireActivity()
					val vmClass = getViewModelClass(activity)
					ViewModelProvider.create(
						store = activity.viewModelStore,
						factory = activity.defaultViewModelProviderFactory,
						extras = activity.defaultViewModelCreationExtras,
					)[vmClass].also { cached = it }
				} else {
					viewModel
				}
			}

		override fun isInitialized(): Boolean = cached != null

		private fun getViewModelClass(activity: Activity) = when (activity) {
			is ReaderActivity -> ReaderViewModel::class.java
			is DetailsActivity -> DetailsViewModel::class.java
			else -> error("Wrong activity ${activity.javaClass.simpleName} for ${ChaptersPagesViewModel::class.java.simpleName}")
		}
	}
}
