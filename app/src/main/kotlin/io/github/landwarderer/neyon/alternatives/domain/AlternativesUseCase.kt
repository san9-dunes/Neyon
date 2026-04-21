package io.github.landwarderer.neyon.alternatives.domain

import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.util.ext.toLocale
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import io.github.landwarderer.neyon.search.domain.SearchKind
import io.github.landwarderer.neyon.search.domain.SearchV2Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.Locale
import javax.inject.Inject

private const val PARALLEL_SEARCH_LIMIT = 5

class AlternativesUseCase @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	/**
	 * Streams all alternative manga found across [sources] as a [Flow].
	 * Up to [PARALLEL_SEARCH_LIMIT] sources are searched concurrently (mirrors SearchViewModel).
	 * Used by [AutoFixUseCase] which needs an incremental Flow<Manga>.
	 */
	suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
		val sources = getSources(manga.source, throughDisabledSources)
		if (sources.isEmpty()) return emptyFlow()
		val cleanTitle = cleanTitle(manga.title) ?: return emptyFlow()

		return channelFlow {
			val semaphore = Semaphore(PARALLEL_SEARCH_LIMIT)
			for (source in sources) {
				launch(Dispatchers.IO) {
					semaphore.withPermit {
						val result = searchSource(source, cleanTitle, manga.id)
						result.getOrNull()?.forEach { send(it) }
					}
				}
			}
		}
	}

	/** Returns all sources to search, ordered by relevance to the reference source. */
	suspend fun getSources(ref: MangaSource, throughDisabledSources: Boolean): List<MangaSource> {
		val enabled = sourcesRepository.getEnabledSources()
		val sources: List<MangaSource> = if (throughDisabledSources) {
			enabled + sourcesRepository.getDisabledSources()
		} else {
			enabled
		}
		return sources.distinctBy { it.name }.sortedByDescending { it.priority(ref) }
	}

	/** Cleans a manga title for use as a search query. Returns null if the title is unusable. */
	fun cleanTitle(title: String): String? {
		val clean = title
			.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
			.replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
			.trim()
		return clean.ifBlank { null }
	}

	/**
	 * Searches a single [source] for the given [query].
	 * Returns a list of matching manga (excluding [excludeId]), with full details fetched.
	 * Returns null if the source has no results or on error.
	 */
	suspend fun searchSource(source: MangaSource, query: String, excludeId: Long): Result<List<Manga>> =
		runCatchingCancellable {
			val searchHelper = searchHelperFactory.create(source)
			val list = withTimeoutOrNull(15_000L) {
				searchHelper(query, SearchKind.TITLE)?.manga
			} ?: return@runCatchingCancellable emptyList()

			list
				.filter { it.id != excludeId }
				.map { m ->
					runCatchingCancellable {
						mangaRepositoryFactory.create(m.source).getDetails(m)
					}.getOrDefault(m)
				}
		}

	private fun MangaSource.priority(ref: MangaSource): Int {
		var res = 0
		if (this is MangaParserSource && ref is MangaParserSource) {
			if (locale == ref.locale) {
				res += 4
			} else if (locale.toLocale() == Locale.getDefault()) {
				res += 2
			}
			if (contentType == ref.contentType) {
				res++
			}
		}
		return res
	}
}
