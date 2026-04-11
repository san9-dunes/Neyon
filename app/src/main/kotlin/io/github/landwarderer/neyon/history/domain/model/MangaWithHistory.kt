package io.github.landwarderer.neyon.history.domain.model

import io.github.landwarderer.neyon.core.model.MangaHistory
import org.koitharu.kotatsu.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
