package io.github.landwarderer.neyon.core.exceptions

import io.github.landwarderer.neyon.details.ui.pager.EmptyMangaReason
import org.koitharu.kotatsu.parsers.model.Manga

class EmptyMangaException(
    val reason: EmptyMangaReason?,
    val manga: Manga,
    cause: Throwable?
) : IllegalStateException(cause)
