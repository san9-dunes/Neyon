package io.github.landwarderer.neyon.mihon

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.neyon.core.cache.MemoryContentCache
import io.github.landwarderer.neyon.core.db.entity.ChapterEntity
import io.github.landwarderer.neyon.core.exceptions.CloudFlareException
import io.github.landwarderer.neyon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.neyon.core.parser.CachingMangaRepository
import io.github.landwarderer.neyon.mihon.model.MihonMangaSource
import io.github.landwarderer.neyon.mihon.model.asContentPage
import io.github.landwarderer.neyon.mihon.model.getPublicContentUrl
import io.github.landwarderer.neyon.mihon.model.toContent
import io.github.landwarderer.neyon.mihon.model.toContentChapter
import io.github.landwarderer.neyon.mihon.model.toContentListFilter
import io.github.landwarderer.neyon.mihon.model.toMangaPage
import io.github.landwarderer.neyon.mihon.model.toMangaListFilterOptions
import io.github.landwarderer.neyon.mihon.model.toMihonChapter
import io.github.landwarderer.neyon.mihon.model.toMihonManga
import io.github.landwarderer.neyon.mihon.model.toNeyonChapterEntity
import io.github.landwarderer.neyon.mihon.model.toNeyonManga
import io.github.landwarderer.neyon.mihon.model.toNeyonMangaChapter
import io.github.landwarderer.neyon.mihon.model.toNeyonMangaPage
import io.github.landwarderer.neyon.search.domain.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder as ContentSortOrder

/**
 * Repository that adapts a Mihon CatalogueSource to app's ContentRepository interface.
 */
class MihonMangaRepository(
    override val source: MihonMangaSource,
    cache: MemoryContentCache,
    private val extensionManager: dagger.Lazy<MihonExtensionManager>? = null,
) : CachingMangaRepository(cache) {
    
    companion object {
        private const val TAG = "MihonMangaRepository"
        
        private fun extractChapterNumber(name: String): Float {
            // Try Chinese format: 第X话
            val chineseRegex = Regex("""第\s*(\d+(?:\.\d+)?)\s*话""")
            chineseRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            // Try English format: Chapter X, Ch. X
            val englishRegex = Regex("""(?:Chapter|Ch\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            englishRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            // Try pure number
            val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
            numberRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            return -1f
        }
    }

    private var lastOffset = -1
    private var currentPage = 1
    
    val mihonSource: CatalogueSource
        get() = activeMihonSource()
    
    override val sortOrders: Set<ContentSortOrder> = buildSet {
        add(ContentSortOrder.POPULARITY)
        if (mihonSource.supportsLatest) {
            add(ContentSortOrder.UPDATED)
        }
    }
    
    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true,
        )
    
    override var defaultSortOrder: ContentSortOrder = ContentSortOrder.POPULARITY

    suspend fun getMangaDetails(sourceId: Long, url: String): Result<Manga> = withContext(Dispatchers.IO) {
        runCatching {
            val (wrappedSource, catalogueSource) = activeMihonSourcePair(sourceId)
            val sManga = SManga.create().apply {
                this.url = url
                this.title = url
            }
            val details = rethrowMihonWrappedExceptions {
                catalogueSource.getMangaDetails(sManga)
            }
            details.url = sManga.url
            val publicUrl = (catalogueSource as? HttpSource)?.getPublicContentUrl(details) ?: ""
            details.toNeyonManga(
                source = wrappedSource,
                publicUrl = publicUrl,
            )
        }
    }

    suspend fun getChapters(sourceId: Long, url: String, mangaId: Long = 0L): Result<List<ChapterEntity>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (wrappedSource, catalogueSource) = activeMihonSourcePair(sourceId)
                val sManga = SManga.create().apply {
                    this.url = url
                    this.title = url
                }
                rethrowMihonWrappedExceptions {
                    catalogueSource.getChapterList(sManga)
                }.asReversed().mapIndexed { index, chapter ->
                    val chapterNumber = if (chapter.chapter_number > 0f) {
                        chapter.chapter_number
                    } else {
                        (index + 1).toFloat()
                    }
                    chapter.toNeyonChapterEntity(
                        mangaId = mangaId,
                        source = wrappedSource,
                        index = index,
                    ).copy(number = chapterNumber)
                }
            }
        }

    suspend fun searchManga(
        sourceId: Long = source.sourceId,
        query: String,
        page: Int = 1,
        filters: FilterList? = null,
    ): Result<SearchResults> = withContext(Dispatchers.IO) {
        runCatching {
            val (wrappedSource, catalogueSource) = activeMihonSourcePair(sourceId)
            val filterList = filters ?: runCatching {
                catalogueSource.getFilterList()
            }.getOrDefault(FilterList())
            val networkResult = rethrowMihonWrappedExceptions {
                catalogueSource.getSearchManga(page, query, filterList)
            }
            val mappedManga = networkResult.mangas.map { manga ->
                manga.toNeyonManga(
                    source = wrappedSource,
                    publicUrl = (catalogueSource as? HttpSource)?.getPublicContentUrl(manga) ?: "",
                )
            }
            SearchResults(
                listFilter = MangaListFilter(query = query),
                sortOrder = ContentSortOrder.RELEVANCE,
                manga = mappedManga,
                hasNextPage = networkResult.hasNextPage,
            )
        }
    }
    
    override suspend fun getList(
        offset: Int,
        order: ContentSortOrder?,
        filter: MangaListFilter?,
    ): List<Manga> = withContext(Dispatchers.IO) {
        val (wrappedSource, mihonSource) = activeMihonSourcePair(source.sourceId)
        if (offset == 0) {
            currentPage = 1
        } else if (offset > lastOffset) {
            currentPage++
        }
        lastOffset = offset
        
        val page = currentPage
        val query = filter?.query
        
        val hasFilters = filter?.let { 
            it.query?.isNotBlank() == true || it.tags.isNotEmpty() || it.tagsExclude.isNotEmpty()
        } ?: false
        
        val mangasPage = rethrowMihonWrappedExceptions {
            when {
                hasFilters -> {
                    mihonSource.getSearchManga(page, query ?: "", filter?.toMihonFilterList() ?: FilterList())
                }
                order == ContentSortOrder.UPDATED && mihonSource.supportsLatest -> {
                    mihonSource.getLatestUpdates(page)
                }
                else -> {
                    mihonSource.getPopularManga(page)
                }
            }
        }
        
        mangasPage.mangas.map { sContent ->
            sContent.toNeyonManga(
                source = wrappedSource,
                publicUrl = (mihonSource as? HttpSource)?.getPublicContentUrl(sContent) ?: "",
            ).also {
                android.util.Log.d(TAG, "Mapped to Neyon Manga: ${it.title}")
            }
        }
    }
    
    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val (wrappedSource, mihonSource) = activeMihonSourcePair(source.sourceId)
        val content = manga.toContent(wrappedSource)
        val sContent = content.toMihonManga()
        
        val details = try {
            rethrowMihonWrappedExceptions {
                mihonSource.getMangaDetails(sContent)
            }
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                rethrowMihonWrappedExceptions {
                    mihonSource.getMangaDetails(sContent)
                }
            } else {
                throw e
            }
        }
        
        val rawChapters = try {
            rethrowMihonWrappedExceptions {
                mihonSource.getChapterList(sContent)
            }
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                rethrowMihonWrappedExceptions {
                    mihonSource.getChapterList(sContent)
                }
            } else {
                throw e
            }
        }
        
        val chapters = rawChapters.asReversed()
            .mapIndexed { index, sChapter ->
                val chapterNumber = if (sChapter.chapter_number > 0) {
                    sChapter.chapter_number
                } else {
                    (index + 1).toFloat()
                }
                sChapter.toNeyonMangaChapter(wrappedSource, chapterNumber)
            }
            .sortedBy { it.number }
        
        // Copy missing fields from original manga to details
        details.url = sContent.url
        
        // Title fallback
        val detailsTitle = try { details.title } catch (e: Exception) { "" }
        if (detailsTitle.isBlank()) {
            details.title = sContent.title
        }
        
        // Thumbnail fallback
        val detailsThumb = try { details.thumbnail_url } catch (e: Exception) { null }
        val searchThumb = try { sContent.thumbnail_url } catch (e: Exception) { null }
        
        if (detailsThumb.isNullOrBlank() || detailsThumb == details.url || detailsThumb == sContent.url) {
            if (!searchThumb.isNullOrBlank()) {
                details.thumbnail_url = searchThumb
            }
        }
        
        val publicUrl = (mihonSource as? HttpSource)?.getPublicContentUrl(details) ?: ""
        
        details.toNeyonManga(
            source = wrappedSource,
            chapters = chapters,
            publicUrl = publicUrl,
        ).copy(id = manga.id)
    }
    
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val (wrappedSource, mihonSource) = activeMihonSourcePair(source.sourceId)
        val contentChapter = chapter.toContentChapter(wrappedSource)
        val sChapter = contentChapter.toMihonChapter()
        val pages = rethrowMihonWrappedExceptions {
            mihonSource.getPageList(sChapter)
        }
        
        pages.mapIndexed { index, page ->
            if (mihonSource !is HttpSource) {
                return@mapIndexed page.toNeyonMangaPage(wrappedSource, sChapter)
            }

            val headers = try {
                if (!page.imageUrl.isNullOrBlank()) {
                    val h = mihonSource.getPageHeaders(page)
                    val map = mutableMapOf<String, String>()
                    for (i in 0 until h.size) {
                        map[h.name(i)] = h.value(i)
                    }
                    map
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }

            page.asContentPage(wrappedSource, sChapter, headers).let { contentPage ->
                val updatedPage = if (page.imageUrl.isNullOrBlank() && page.url.isNotBlank()) {
                    contentPage.copy(
                        url = "mihon://resolve?page_url=${java.net.URLEncoder.encode(page.url, "UTF-8")}&index=$index"
                    )
                } else if (!page.imageUrl.isNullOrBlank() && page.url.isNotBlank() && page.url != page.imageUrl) {
                    contentPage.copy(
                        url = "mihon://image?page_url=${java.net.URLEncoder.encode(page.url, "UTF-8")}&image_url=${java.net.URLEncoder.encode(page.imageUrl!!, "UTF-8")}&index=$index"
                    )
                } else {
                    contentPage
                }
                updatedPage.toMangaPage()
            }
        }
    }
    
    override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        val mihonSource = activeMihonSource()
        val url = page.url
        
        if (url.startsWith("mihon://")) {
            val uri = android.net.Uri.parse(url)
            if (url.startsWith("mihon://image")) {
                val imageUrl = uri.getQueryParameter("image_url")
                if (!imageUrl.isNullOrBlank()) return@withContext imageUrl
            } else if (url.startsWith("mihon://resolve")) {
                val pageUrl = uri.getQueryParameter("page_url")
                if (!pageUrl.isNullOrBlank()) {
                    val mihonPage = Page(0, pageUrl)
                    val httpSource = mihonSource as? HttpSource
                    if (httpSource != null) {
                        return@withContext rethrowMihonWrappedExceptions {
                            httpSource.getImageUrl(mihonPage)
                        }
                    }
                    return@withContext pageUrl
                }
            }
            return@withContext url
        } else {
            url
        }
    }
    
    override suspend fun getFilterOptions(): MangaListFilterOptions {
        val (wrappedSource, mihonSource) = activeMihonSourcePair(source.sourceId)
        val mihonFilters = try {
            mihonSource.getFilterList()
        } catch (e: Exception) {
            FilterList()
        }
        
        val options = MihonFilterMapper.mapOptions(mihonFilters, wrappedSource)
        return options.toMangaListFilterOptions()
    }

    private fun MangaListFilter.toMihonFilterList(): FilterList {
        val mihonSource = activeMihonSource()
        val mihonFilters = try {
            mihonSource.getFilterList()
        } catch (e: Exception) {
            return FilterList()
        }
        
        MihonFilterMapper.updateMihonFilters(mihonFilters, this.toContentListFilter())
        return mihonFilters
    }
    
    fun getRequestHeaders(): Map<String, String> {
        val mihonSource = activeMihonSource()
        val httpSource = mihonSource as? HttpSource ?: return emptyMap()
        val headers = httpSource.headers
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        return map
    }

    fun getImageClient(): okhttp3.OkHttpClient? {
        val mihonSource = activeMihonSource()
        return (mihonSource as? HttpSource)?.client
    }
    
    fun createPageRequest(pageUrl: String, page: MangaPage): okhttp3.Request {
        val mihonSource = activeMihonSource()
        if (pageUrl.isBlank()) return okhttp3.Request.Builder().url("http://localhost").build() // Dummy
        val httpSource = mihonSource as? HttpSource ?: return okhttp3.Request.Builder().url(pageUrl).build()
        val uri = android.net.Uri.parse(page.url)
        val originalPageUrl = if (page.url.startsWith("mihon://")) {
            uri.getQueryParameter("page_url")
        } else {
            null
        }
        val pageIndex = if (page.url.startsWith("mihon://")) {
            uri.getQueryParameter("index")?.toIntOrNull()
        } else {
            null
        }
        val sPage = Page(
            index = pageIndex ?: 0,
            url = originalPageUrl ?: pageUrl,
            imageUrl = pageUrl,
        )
        return httpSource.imageRequest(sPage)
    }

    fun createCoverRequest(imageUrl: String): okhttp3.Request {
        val mihonSource = activeMihonSource()
        val httpSource = mihonSource as? HttpSource ?: return okhttp3.Request.Builder().url(imageUrl).build()
        return try {
            val sPage = Page(0, imageUrl = imageUrl)
            httpSource.imageRequest(sPage)
        } catch (e: Throwable) {
            okhttp3.Request.Builder().url(imageUrl).build()
        }
    }

    private inline fun <T> rethrowMihonWrappedExceptions(block: () -> T): T {
        try {
            return block()
        } catch (e: RuntimeException) {
            when (val cause = e.cause) {
                is CloudFlareException -> throw cause
                is InteractiveActionRequiredException -> throw cause
                is java.io.IOException -> throw cause
                else -> throw e
            }
        }
    }

    private fun activeMihonSource(): CatalogueSource {
        return activeMihonSourcePair(source.sourceId).second
    }

    private fun activeMihonSourcePair(sourceId: Long): Pair<MihonMangaSource, CatalogueSource> {
        val manager = extensionManager?.get()
        val wrappedSource = manager?.getMihonMangaSourceById(sourceId)
            ?: source.takeIf { it.sourceId == sourceId }
            ?: throw IllegalStateException("Extension source not found: $sourceId")
        val catalogueSource = manager?.getCatalogueSourceById(sourceId)
            ?: wrappedSource.catalogueSource
        return wrappedSource to catalogueSource
    }
    
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

    suspend fun getFavicons(): org.koitharu.kotatsu.parsers.model.Favicons {
        return org.koitharu.kotatsu.parsers.model.Favicons(emptyList(), "")
    }
}
