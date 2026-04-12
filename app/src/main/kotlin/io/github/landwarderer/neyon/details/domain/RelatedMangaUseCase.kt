package io.github.landwarderer.neyon.details.domain

import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.neyon.search.domain.SearchKind
import io.github.landwarderer.neyon.search.domain.SearchV2Helper
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

class RelatedMangaUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val searchHelperFactory: SearchV2Helper.Factory,
) {

	suspend operator fun invoke(seed: Manga) = runCatchingCancellable {
		val nativeRelated = mangaRepositoryFactory.create(seed.source).getRelated(seed)

		val baseTitle = getBaseTitle(seed.title)
		val fallbackRelated = if (baseTitle.isNotEmpty() && baseTitle.length > 3 && baseTitle != seed.title) {
			val searchHelper = searchHelperFactory.create(seed.source)
			val results = searchHelper(baseTitle, SearchKind.SIMPLE)?.manga.orEmpty()
			results
				.filter { it.url != seed.url && (it.id != seed.id || it.source != seed.source) }
				.sortedBy { it.title }
		} else {
			emptyList()
		}

		(fallbackRelated + nativeRelated).distinctBy { it.url }
	}.onFailure {
		it.printStackTraceDebug("RelatedMangaUseCase::invoke")
	}.getOrNull()

	private fun getBaseTitle(title: String): String {
		// Used to catch most sequels: "Manga Part 2", "Manga Season 3", "Manga v4", "Manga 2", and "Manga II"
		val regex = Regex("(?i)\\s+(?:part|season|vol|v|s|ch|chapter|ep|episode)\\.?\\s*\\d+.*\$|\\s+(?:\\d+|[IVXLCDM]+)\$")
		return title.replace(regex, "").trim()
	}
}
