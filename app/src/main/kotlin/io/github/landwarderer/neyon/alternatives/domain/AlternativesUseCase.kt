package io.github.landwarderer.neyon.alternatives.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.util.ext.toLocale
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import io.github.landwarderer.neyon.search.domain.SearchKind
import io.github.landwarderer.neyon.search.domain.SearchV2Helper
import java.util.Locale
import javax.inject.Inject

class AlternativesUseCase @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
		val sources = getSources(manga.source, throughDisabledSources)
		if (sources.isEmpty()) {
			return emptyFlow()
		}



		val cleanTitle = manga.title.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "").replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ").trim()
		if (cleanTitle.isBlank()) return emptyFlow()

		return channelFlow {
			for (source in sources) {
				launch {
					val searchHelper = searchHelperFactory.create(source)
					val list = runCatchingCancellable {
						withTimeoutOrNull(15000L) {
							searchHelper(cleanTitle, SearchKind.TITLE)?.manga
						}
					}.getOrNull()
					list?.forEach { m ->
						if (m.id != manga.id) {
							launch {
								val details = runCatchingCancellable {
									mangaRepositoryFactory.create(m.source).getDetails(m)
								}.getOrDefault(m)
								send(details)
							}
						}
					}
				}
			}
		}
	}

	private suspend fun getSources(ref: MangaSource, disabled: Boolean): List<MangaSource> {
		val sources = if (disabled) {
			(sourcesRepository.getEnabledSources() + sourcesRepository.getDisabledSources()) as List<MangaSource>
		} else {
			sourcesRepository.getEnabledSources()
		}
		return sources.distinctBy { it.name }.sortedByDescending { it.priority(ref) }
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
