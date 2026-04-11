package io.github.landwarderer.neyon.favourites.domain.model

import io.github.landwarderer.neyon.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
