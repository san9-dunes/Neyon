package io.github.landwarderer.neyon.details.domain

import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.neyon.search.domain.SearchKind
import io.github.landwarderer.neyon.search.domain.SearchV2Helper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

class RelatedMangaUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val searchHelperFactory: SearchV2Helper.Factory,
) {

	suspend operator fun invoke(seed: Manga) = runCatchingCancellable {
		coroutineScope {
			val nativeRelatedDeferred = async {
				mangaRepositoryFactory.create(seed.source).getRelated(seed)
			}
			val fallbackRelatedDeferred = async {
				findFallbackRelated(seed)
			}
			val nativeRelated = nativeRelatedDeferred.await()
			val nativeKeys = nativeRelated.mapTo(HashSet(nativeRelated.size), ::relatedKey)
			(nativeRelated + fallbackRelatedDeferred.await())
				.filterNot { it.url == seed.url && it.source == seed.source }
				.filterNot { it.id == seed.id && it.source == seed.source }
				.distinctBy(::relatedKey)
				.sortedWith(
					compareByDescending<Manga> { sequelScore(seed, it, relatedKey(it) in nativeKeys) }
						.thenBy { it.title },
				)
		}
	}.onFailure {
		it.printStackTraceDebug("RelatedMangaUseCase::invoke")
	}.getOrNull()

	private suspend fun findFallbackRelated(seed: Manga): List<Manga> {
		val queries = buildSearchQueries(seed.title)
		if (queries.isEmpty()) {
			return emptyList()
		}
		val searchHelper = searchHelperFactory.create(seed.source)
		return coroutineScope {
			queries.map { query ->
				async {
					runCatchingCancellable {
						searchHelper(query, SearchKind.SIMPLE)?.manga.orEmpty()
					}.getOrElse { emptyList() }
				}
			}.awaitAll().flatten()
		}
	}

	private fun sequelScore(seed: Manga, candidate: Manga, isNativeRelated: Boolean): Int {
		val baseSeed = normalizeTitle(stripSequelSuffix(seed.title))
		val normalizedCandidate = normalizeTitle(candidate.title)
		var score = if (isNativeRelated) 40 else 0
		if (normalizedCandidate.startsWith(baseSeed)) {
			score += 80
		} else if (normalizedCandidate.contains(baseSeed)) {
			score += 50
		}
		if (candidate.altTitle?.let(::normalizeTitle)?.contains(baseSeed) == true) {
			score += 20
		}
		val seedSeq = extractTrailingSequence(seed.title)
		val candidateSeq = extractTrailingSequence(candidate.title)
		score += when {
			seedSeq != null && candidateSeq != null && candidateSeq == seedSeq + 1 -> 120
			seedSeq != null && candidateSeq != null && candidateSeq > seedSeq -> 90
			seedSeq == null && candidateSeq != null -> 30
			else -> 0
		}
		return score
	}

	private fun buildSearchQueries(title: String): List<String> {
		val baseTitle = stripSequelSuffix(title)
		return linkedSetOf(
			title.trim(),
			baseTitle,
			baseTitle.substringBefore(":", baseTitle).trim(),
			baseTitle.substringBefore(" - ", baseTitle).trim(),
		).filter { it.length > 3 }
	}

	private fun stripSequelSuffix(title: String): String {
		var result = title.trim()
		repeat(3) {
			val simplified = result
				.replace(TRAILING_INDEXED_PART_REGEX, "")
				.replace(TRAILING_NUMBER_REGEX, "")
				.trim()
				.trimEnd(' ', '-', ':', ',', ';', '.', '!', '?', ')', ']', '}')
			if (simplified == result || simplified.length <= 3) {
				return result
			}
			result = simplified
		}
		return result
	}

	private fun normalizeTitle(title: String): String {
		return title.lowercase()
			.replace(NON_ALNUM_REGEX, " ")
			.replace(MULTI_SPACE_REGEX, " ")
			.trim()
	}

	private fun extractTrailingSequence(title: String): Int? {
		val token = TRAILING_INDEXED_PART_REGEX.find(title)?.groupValues?.getOrNull(1)
			?: TRAILING_NUMBER_REGEX.find(title)?.groupValues?.getOrNull(1)
		return token?.let(::parseSequenceToken)
	}

	private fun parseSequenceToken(token: String): Int? {
		return token.toIntOrNull() ?: romanToInt(token.uppercase())
	}

	private fun romanToInt(value: String): Int? {
		var total = 0
		var prev = 0
		for (c in value.reversed()) {
			val curr = when (c) {
				'I' -> 1
				'V' -> 5
				'X' -> 10
				'L' -> 50
				'C' -> 100
				'D' -> 500
				'M' -> 1000
				else -> return null
			}
			total += if (curr < prev) -curr else curr
			prev = curr
		}
		return if (total > 0) total else null
	}

	private fun relatedKey(manga: Manga): String {
		return if (manga.url.isNotEmpty()) {
			"${manga.source.name}:${manga.url}"
		} else {
			"${manga.source.name}:${manga.id}"
		}
	}

	private companion object {
		val TRAILING_INDEXED_PART_REGEX = Regex(
			"(?i)(?:\\s*[-:,.()\\[\\]{}]*)?(?:part|season|vol(?:ume)?|v|ch(?:apter)?|ep(?:isode)?)\\.?\\s*([0-9]+|[ivxlcdm]+)\\s*$",
		)
		val TRAILING_NUMBER_REGEX = Regex("(?i)(?:\\s*[-:,.()\\[\\]{}]*)?([0-9]+|[ivxlcdm]+)\\s*$")
		val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}]+")
		val MULTI_SPACE_REGEX = Regex("\\s+")
	}
}
