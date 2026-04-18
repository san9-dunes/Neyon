package io.github.landwarderer.neyon.reader.ui

import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.bookmarks.domain.Bookmark
import io.github.landwarderer.neyon.bookmarks.domain.BookmarksRepository
import io.github.landwarderer.neyon.core.exceptions.EmptyMangaException
import io.github.landwarderer.neyon.core.model.getPreferredBranch
import io.github.landwarderer.neyon.core.nav.MangaIntent
import io.github.landwarderer.neyon.core.nav.ReaderIntent
import io.github.landwarderer.neyon.core.os.AppShortcutManager
import io.github.landwarderer.neyon.core.parser.MangaDataRepository
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.ReaderMode
import io.github.landwarderer.neyon.core.prefs.TriStateOption
import io.github.landwarderer.neyon.core.prefs.observeAsFlow
import io.github.landwarderer.neyon.core.prefs.observeAsStateFlow
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.core.util.ext.firstNotNull
import io.github.landwarderer.neyon.core.util.ext.requireValue
import io.github.landwarderer.neyon.details.data.MangaDetails
import io.github.landwarderer.neyon.details.domain.DetailsInteractor
import io.github.landwarderer.neyon.details.domain.DetailsLoadUseCase
import io.github.landwarderer.neyon.details.domain.ProgressUpdateUseCase
import io.github.landwarderer.neyon.details.ui.pager.ChaptersPagesViewModel
import io.github.landwarderer.neyon.details.ui.pager.EmptyMangaReason
import io.github.landwarderer.neyon.download.ui.worker.DownloadWorker
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.history.domain.HistoryUpdateUseCase
import io.github.landwarderer.neyon.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import io.github.landwarderer.neyon.local.data.LocalStorageChanges
import io.github.landwarderer.neyon.local.domain.DeleteLocalMangaUseCase
import io.github.landwarderer.neyon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.sizeOrZero
import io.github.landwarderer.neyon.reader.domain.ChaptersLoader
import io.github.landwarderer.neyon.reader.domain.DetectReaderModeUseCase
import io.github.landwarderer.neyon.reader.domain.PageLoader
import io.github.landwarderer.neyon.reader.ui.config.ReaderSettings
import io.github.landwarderer.neyon.reader.ui.pager.ReaderUiState
import io.github.landwarderer.neyon.reader.ui.pager.ReaderPage
import io.github.landwarderer.neyon.scrobbling.discord.ui.DiscordRpc
import io.github.landwarderer.neyon.stats.domain.StatsCollector
import java.time.Instant
import javax.inject.Inject

private const val BOUNDS_PAGE_OFFSET = 2

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: MangaDataRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val hiddenPagesStore: HiddenPagesStore,
    settings: AppSettings,
    private val pageLoader: PageLoader,
    private val chaptersLoader: ChaptersLoader,
    private val appShortcutManager: AppShortcutManager,
    private val detailsLoadUseCase: DetailsLoadUseCase,
    private val historyUpdateUseCase: HistoryUpdateUseCase,
    private val detectReaderModeUseCase: DetectReaderModeUseCase,
    private val progressUpdateUseCase: ProgressUpdateUseCase,
    private val statsCollector: StatsCollector,
    private val discordRpc: DiscordRpc,
    @LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
    interactor: DetailsInteractor,
    deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
    downloadScheduler: DownloadWorker.Scheduler,
    readerSettingsProducerFactory: ReaderSettings.Producer.Factory,
) : ChaptersPagesViewModel(
    settings = settings,
    interactor = interactor,
    bookmarksRepository = bookmarksRepository,
    historyRepository = historyRepository,
    downloadScheduler = downloadScheduler,
    deleteLocalMangaUseCase = deleteLocalMangaUseCase,
    localStorageChanges = localStorageChanges,
) {
    private val intent = MangaIntent(savedStateHandle)

    private var loadingJob: Job? = null
    private var pageSaveJob: Job? = null
    private var bookmarkJob: Job? = null
    private var stateChangeJob: Job? = null

    // Single merged init — order matters: details must be set before loadImpl() runs
    init {
        mangaDetails.value = intent.manga?.let { MangaDetails(it) }
        initIncognitoMode()
        loadImpl()
        launchJob(Dispatchers.IO) {
            val mangaId = manga.filterNotNull().first().id
            if (!isIncognitoMode.firstNotNull()) {
                appShortcutManager.notifyMangaOpened(mangaId)
            }
        }
    }

    val readerMode = MutableStateFlow<ReaderMode?>(null)
    val onPageSaved = MutableEventFlow<Collection<Uri>>()
    val onLoadingError = MutableEventFlow<Throwable>()
    val onShowToast = MutableEventFlow<Int>()
    val onAskNsfwIncognito = MutableEventFlow<Unit>()
    val uiState = MutableStateFlow<ReaderUiState?>(null)

    val pagePreloadLimit: Int
        get() = settings.pagePreloadLimit

    val isIncognitoMode = MutableStateFlow(savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO))

    val content = MutableStateFlow(ReaderContent(emptyList(), null))

    val pageAnimation = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_READER_ANIMATION,
        valueProducer = { readerAnimation },
    )

    val isInfoBarEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_READER_BAR,
        valueProducer = { isReaderBarEnabled },
    )

    val isInfoBarTransparent = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_READER_BAR_TRANSPARENT,
        valueProducer = { isReaderBarTransparent },
    )

    val isKeepScreenOnEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_READER_SCREEN_ON,
        valueProducer = { isReaderKeepScreenOn },
    )

    val isWebtoonZooEnabled = observeIsWebtoonZoomEnabled()
        .stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, false)

    val isWebtoonGapsEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_WEBTOON_GAPS,
        valueProducer = { isWebtoonGapsEnabled },
    )

    val isWebtoonPullGestureEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_WEBTOON_PULL_GESTURE,
        valueProducer = { isWebtoonPullGestureEnabled },
    )

    val defaultWebtoonZoomOut = observeIsWebtoonZoomEnabled().flatMapLatest {
        if (it) {
            observeWebtoonZoomOut()
        } else {
            flowOf(0f)
        }
    }.flowOn(Dispatchers.IO)

    val isZoomControlsEnabled = getObserveIsZoomControlEnabled().flatMapLatest { zoom ->
        if (zoom) {
            combine(readerMode, isWebtoonZooEnabled) { mode, ze -> ze || mode != ReaderMode.WEBTOON }
        } else {
            flowOf(false)
        }
    }.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, false)

    val readerSettingsProducer = readerSettingsProducerFactory.create(
        manga.mapNotNull { it?.id },
    )

    val isMangaNsfw = manga.map { it?.contentRating == ContentRating.ADULT }

    val isBookmarkAdded = readingState.flatMapLatest { state ->
        val manga = mangaDetails.value?.toManga()
        if (state == null || manga == null) {
            flowOf(false)
        } else {
            bookmarksRepository.observeBookmark(manga, state.chapterId, state.page)
                .map {
                    it != null && it.chapterId == state.chapterId && it.page == state.page
                }
        }
    }.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, false)



    fun reload() {
        loadingJob?.cancel()
        loadImpl()
    }

    fun onPause() {
        getMangaOrNull()?.let {
            statsCollector.onPause(it.id)
        }
    }

    fun onStop() {
        discordRpc.clearRpc()
    }

    fun onIdle() {
        discordRpc.setIdle()
    }

    fun switchMode(newMode: ReaderMode) {
        launchJob {
            val manga = checkNotNull(getMangaOrNull())
            dataRepository.saveReaderMode(
                manga = manga,
                mode = newMode,
            )
            readerMode.value = newMode
            content.update {
                it.copy(state = getCurrentState())
            }
        }
    }

    fun saveCurrentState(state: ReaderState? = null) {
        if (state != null) {
            readingState.value = state
            savedStateHandle[ReaderIntent.EXTRA_STATE] = state
        }
        if (isIncognitoMode.value != false) {
            return
        }
        val readerState = state ?: readingState.value ?: return
        historyUpdateUseCase.invokeAsync(
            manga = getMangaOrNull() ?: return,
            readerState = readerState,
            percent = computePercent(readerState.chapterId, readerState.page),
        )
    }

    fun getCurrentState() = readingState.value

    fun getCurrentChapterPages(): List<MangaPage>? {
        val chapterId = readingState.value?.chapterId ?: return null
        return chaptersLoader.getPages(chapterId)
    }

    fun saveCurrentPage(
        pageSaveHelper: PageSaveHelper
    ) {
        val prevJob = pageSaveJob
        pageSaveJob = launchLoadingJob(Dispatchers.IO) {
            prevJob?.cancelAndJoin()
            val state = checkNotNull(getCurrentState())
            val currentManga = manga.requireValue()
            val task = PageSaveHelper.Task(
                manga = currentManga,
                chapterId = state.chapterId,
                pageNumber = state.page + 1,
                page = checkNotNull(getCurrentPage()) { "Cannot find current page" },
            )
            val dest = pageSaveHelper.save(setOf(task))
            onPageSaved.call(dest)
        }
    }

    fun getCurrentPage(): MangaPage? {
        val state = readingState.value ?: return null
        return content.value.pages.find {
            it.chapterId == state.chapterId && it.index == state.page
        }?.toMangaPage()
    }

    fun switchChapter(id: Long, page: Int) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.IO) {
            prevJob?.cancelAndJoin()
            content.value = ReaderContent(emptyList(), null)
            chaptersLoader.loadSingleChapter(id)
            val newState = ReaderState(id, page, 0)
            content.value = ReaderContent(getFilteredSnapshot(), newState)
            saveCurrentState(newState)
        }
    }

    fun switchChapterBy(delta: Int) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.IO) {
            prevJob?.cancelAndJoin()
            val prevState = readingState.requireValue()
            val newChapterId = if (delta != 0) {
                val allChapters = mangaDetails.requireValue().allChapters
                var index = allChapters.indexOfFirst { x -> x.id == prevState.chapterId }
                if (index < 0) {
                    return@launchLoadingJob
                }
                index += delta
                (allChapters.getOrNull(index) ?: return@launchLoadingJob).id
            } else {
                prevState.chapterId
            }
            content.value = ReaderContent(emptyList(), null)
            chaptersLoader.loadSingleChapter(newChapterId)
            val newState = ReaderState(
                chapterId = newChapterId,
                page = if (delta == 0) prevState.page else 0,
                scroll = if (delta == 0) prevState.scroll else 0,
            )
            content.value = ReaderContent(getFilteredSnapshot(), newState)
            saveCurrentState(newState)
        }
    }

    @MainThread
    fun onCurrentPageChanged(lowerPos: Int, upperPos: Int) {
        val prevJob = stateChangeJob
        val pages = content.value.pages // capture immediately on main thread
        stateChangeJob = launchJob(Dispatchers.IO) {
            prevJob?.cancelAndJoin()
            loadingJob?.join()
            // If pages changed mid-flight (chapter boundary load completed while we waited),
            // fall back to the latest snapshot so state/history/stats are never silently dropped.
            val effectivePages = if (pages.size != content.value.pages.size) {
                content.value.pages
            } else {
                pages
            }
            val centerPos = (lowerPos + upperPos) / 2
            effectivePages.getOrNull(centerPos)?.let { page ->
                readingState.update { cs ->
                    cs?.copy(chapterId = page.chapterId, page = page.index)
                }
            }
            notifyStateChanged()
            if (effectivePages.isEmpty() || loadingJob?.isActive == true) {
                return@launchJob
            }
            ensureActive()
            val autoLoadAllowed = readerMode.value != ReaderMode.WEBTOON || !isWebtoonPullGestureEnabled.value
            if (autoLoadAllowed) {
                if (upperPos >= effectivePages.lastIndex - BOUNDS_PAGE_OFFSET) {
                    loadPrevNextChapter(effectivePages.last().chapterId, isNext = true)
                }
                if (lowerPos <= BOUNDS_PAGE_OFFSET) {
                    loadPrevNextChapter(effectivePages.first().chapterId, isNext = false)
                }
            }
            // Single prefetch path — uses PageLoader which handles disk cache, rate-limiting,
            // and per-source authentication headers correctly.
            if (pageLoader.isPrefetchApplicable()) {
                pageLoader.prefetch(effectivePages.trySublist(upperPos + 1, upperPos + pagePreloadLimit))
            }
            // Evict preview thumbnails for pages far behind from Coil's memory cache.
            val trailingEvictLimit = (lowerPos - 4).coerceAtLeast(0)
            if (trailingEvictLimit > 0) {
                val pagesToEvict = effectivePages.trySublist(0, trailingEvictLimit)
                pageLoader.evictFromMemoryCache(pagesToEvict.map { it.toMangaPage() })
            }
        }
    }

    fun toggleBookmark() {
        if (bookmarkJob?.isActive == true) {
            return
        }
        bookmarkJob = launchJob(Dispatchers.IO) {
            loadingJob?.join()
            val state = checkNotNull(getCurrentState())
            if (isBookmarkAdded.value) {
                val manga = requireManga()
                bookmarksRepository.removeBookmark(manga.id, state.chapterId, state.page)
                onShowToast.call(R.string.bookmark_removed)
            } else {
                val page = checkNotNull(getCurrentPage()) { "Page not found" }
                val bookmark = Bookmark(
                    manga = requireManga(),
                    pageId = page.id,
                    chapterId = state.chapterId,
                    page = state.page,
                    scroll = state.scroll,
                    imageUrl = page.preview.ifNullOrEmpty { page.url },
                    createdAt = Instant.now(),
                    percent = computePercent(state.chapterId, state.page),
                )
                bookmarksRepository.addBookmark(bookmark)
                onShowToast.call(R.string.bookmark_added)
            }
        }
    }

    fun setIncognitoMode(value: Boolean, dontAskAgain: Boolean) {
        isIncognitoMode.value = value
        if (dontAskAgain) {
            settings.incognitoModeForNsfw = if (value) TriStateOption.ENABLED else TriStateOption.DISABLED
        }
    }

    fun updateReadingProgress() {
        // Update disk cache for current page and record final reading progress.
        // Called from onDestroy — use launchJob so it's scoped to viewModelScope.
        val page = getCurrentPage() ?: return
        val currentManga = getMangaOrNull() ?: return
        launchJob(Dispatchers.IO) {
            runCatchingCancellable { pageLoader.updateCache(page) }
            progressUpdateUseCase(currentManga)
        }
    }

    private fun loadImpl() {
        loadingJob = launchLoadingJob(Dispatchers.IO + EventExceptionHandler(onLoadingError)) {
            var exception: Exception? = null
            var loadedDetails: MangaDetails? = null
            try {
                detailsLoadUseCase(intent, force = false)
                    .collect { details ->
                        loadedDetails = details
                        if (mangaDetails.value == null) {
                            mangaDetails.value = details
                        }
                        chaptersLoader.init(details)
                        val manga = details.toManga()
                        // obtain state
                        if (readingState.value == null) {
                            val newState = getStateFromIntent(manga)
                            if (newState == null) {
                                return@collect // manga not loaded yet if cannot get state
                            }
                            readingState.value = newState
                            val branch = chaptersLoader.peekChapter(newState.chapterId)?.branch
                            selectedBranch.value = branch
                            // Load the chapter first so its pages can be reused by detectReaderModeUseCase,
                            // eliminating the redundant double getPages() network call on first open.
                            try {
                                chaptersLoader.loadSingleChapter(newState.chapterId)
                            } catch (e: Exception) {
                                readingState.value = null // try next time
                                exception = e.mergeWith(exception)
                                return@collect
                            }
                            val loadedPages = chaptersLoader.getPages(newState.chapterId)
                            val mode = runCatchingCancellable {
                                detectReaderModeUseCase(manga, newState, loadedPages.ifEmpty { null })
                            }.getOrDefault(settings.defaultReaderMode)
                            readerMode.value = mode
                        }
                        mangaDetails.value = details.filterChapters(selectedBranch.value)

                        // save state
                        if (!isIncognitoMode.firstNotNull()) {
                            readingState.value?.let {
                                val percent = computePercent(it.chapterId, it.page)
                                historyUpdateUseCase(manga, it, percent)
                            }
                        }
                        notifyStateChanged()
                        content.value = ReaderContent(getFilteredSnapshot(), readingState.value)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exception = e.mergeWith(exception)
            }
            if (readingState.value == null) {
                val loadedManga = loadedDetails // for smart cast
                if (loadedManga != null) {
                    mangaDetails.value = loadedManga.filterChapters(selectedBranch.value)
                }
                val loadingError = when {
                    exception != null -> exception
                    loadedManga == null || !loadedManga.isLoaded -> null
                    loadedManga.isRestricted -> EmptyMangaException(
                        EmptyMangaReason.RESTRICTED,
                        loadedManga.toManga(),
                        null,
                    )

                    loadedManga.allChapters.isEmpty() -> EmptyMangaException(
                        EmptyMangaReason.NO_CHAPTERS,
                        loadedManga.toManga(),
                        null,
                    )

                    else -> null
                } ?: IllegalStateException("Unable to load manga. This should never happen. Please report")
                onLoadingError.call(loadingError)
            } else exception?.let { e ->
                // manga has been loaded but error occurred
                errorEvent.call(e)
            }
        }
    }

    @AnyThread
    private fun loadPrevNextChapter(currentId: Long, isNext: Boolean) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.IO) {
            prevJob?.join()
            chaptersLoader.loadPrevNextChapter(mangaDetails.requireValue(), currentId, isNext)
            content.value = ReaderContent(getFilteredSnapshot(), null)
        }
    }

    private fun <T> List<T>.trySublist(fromIndex: Int, toIndex: Int): List<T> {
        val fromIndexBounded = fromIndex.coerceAtMost(lastIndex)
        val toIndexBounded = toIndex.coerceIn(fromIndexBounded, lastIndex)
        return if (fromIndexBounded == toIndexBounded) {
            emptyList()
        } else {
            subList(fromIndexBounded, toIndexBounded)
        }
    }

    @WorkerThread
    private fun notifyStateChanged() {
        val state = getCurrentState() ?: return
        val chapter = chaptersLoader.peekChapter(state.chapterId) ?: return
        val m = mangaDetails.value ?: return
        val chapterIndex = m.chapters[chapter.branch]?.indexOfFirst { it.id == chapter.id } ?: -1
        val newState = ReaderUiState(
            mangaName = m.toManga().title,
            chapter = chapter,
            chapterIndex = chapterIndex,
            chaptersTotal = m.chapters[chapter.branch].sizeOrZero(),
            totalPages = chaptersLoader.getPagesCount(chapter.id),
            currentPage = state.page,
            percent = computePercent(state.chapterId, state.page),
            incognito = isIncognitoMode.value == true,
        )
        uiState.value = newState
        if (isIncognitoMode.value == false) {
            statsCollector.onStateChanged(m.id, state)
            discordRpc.updateRpc(m.toManga(), newState)
        }
    }

    private fun computePercent(chapterId: Long, pageIndex: Int): Float {
        val branch = chaptersLoader.peekChapter(chapterId)?.branch
        val chapters = mangaDetails.value?.chapters?.get(branch) ?: return PROGRESS_NONE
        val chaptersCount = chapters.size
        val chapterIndex = chapters.indexOfFirst { x -> x.id == chapterId }
        val pagesCount = chaptersLoader.getPagesCount(chapterId)
        if (chaptersCount == 0 || pagesCount == 0) {
            return PROGRESS_NONE
        }
        val pagePercent = (pageIndex + 1) / pagesCount.toFloat()
        val ppc = 1f / chaptersCount
        return ppc * chapterIndex + ppc * pagePercent
    }

    private fun observeIsWebtoonZoomEnabled() = settings.observeAsFlow(
        key = AppSettings.KEY_WEBTOON_ZOOM,
        valueProducer = { isWebtoonZoomEnabled },
    )

    private fun observeWebtoonZoomOut() = settings.observeAsFlow(
        key = AppSettings.KEY_WEBTOON_ZOOM_OUT,
        valueProducer = { defaultWebtoonZoomOut },
    )

    private fun getObserveIsZoomControlEnabled() = settings.observeAsFlow(
        key = AppSettings.KEY_READER_ZOOM_BUTTONS,
        valueProducer = { isReaderZoomButtonsEnabled },
    )

    private fun initIncognitoMode() {
        if (isIncognitoMode.value != null) {
            return
        }
        launchJob(Dispatchers.IO) {
            interactor.observeIncognitoMode(manga)
                .collect {
                    when (it) {
                        TriStateOption.ENABLED -> isIncognitoMode.value = true
                        TriStateOption.ASK -> {
                            onAskNsfwIncognito.call(Unit)
                            return@collect
                        }

                        TriStateOption.DISABLED -> isIncognitoMode.value = false
                    }
                }
        }
    }

    private suspend fun getStateFromIntent(manga: Manga): ReaderState? {
        // check if we have at least some chapters loaded
        if (manga.chapters.isNullOrEmpty()) {
            return null
        }
        // specific state is requested
        val requestedState: ReaderState? = savedStateHandle[ReaderIntent.EXTRA_STATE]
        if (requestedState != null) {
            return if (manga.findChapterById(requestedState.chapterId) != null) {
                requestedState
            } else {
                null
            }
        }

        val requestedBranch: String? = savedStateHandle[ReaderIntent.EXTRA_BRANCH]
        // continue reading
        val history = historyRepository.getOne(manga)
        if (history != null) {
            val chapter = manga.findChapterById(history.chapterId) ?: return null
            // specified branch is requested
            return if (ReaderIntent.EXTRA_BRANCH in savedStateHandle) {
                if (chapter.branch == requestedBranch) {
                    ReaderState(history)
                } else {
                    ReaderState(manga, requestedBranch)
                }
            } else {
                ReaderState(history)
            }
        }

        // start from beginning
        val preferredBranch = requestedBranch ?: manga.getPreferredBranch(null)
        return ReaderState(manga, preferredBranch)
    }


    private fun getFilteredSnapshot(): List<ReaderPage> {
        val snapshot = chaptersLoader.snapshot()
        val chapterIds = snapshot.map { it.chapterId }.distinct()
        val hiddenUrls = chapterIds.flatMap { 
            hiddenPagesStore.getHiddenPages(it) 
        }.toSet()
        
        return if (hiddenUrls.isEmpty()) {
            snapshot
        } else {
            snapshot.filterNot { hiddenUrls.contains(it.url) }
        }
    }

    fun hideCurrentPage() {
        val state = readingState.value ?: return
        val currentManga = getMangaOrNull() ?: return
        val pages = chaptersLoader.getPages(state.chapterId) ?: return
        val hiddenPage = pages.getOrNull(state.page) ?: return
        
        hiddenPagesStore.addHiddenPage(state.chapterId, hiddenPage.url)
        content.value = ReaderContent(getFilteredSnapshot(), state)
    }

    private fun Exception.mergeWith(other: Exception?): Exception = if (other == null) {
        this
    } else {
        other.addSuppressed(this)
        other
    }
}
