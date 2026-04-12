package io.github.landwarderer.neyon.list.ui.model

import io.github.landwarderer.neyon.core.ui.model.MangaOverride
import org.koitharu.kotatsu.parsers.model.Manga

data class MangaCompactListModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	val subtitle: String,
	override val counter: Int,
	override val isSourceAvailable: Boolean = true,
) : MangaListModel()
