package io.github.landwarderer.neyon.core.parser

import io.github.landwarderer.neyon.core.cache.MemoryContentCache
import io.github.landwarderer.neyon.core.model.TestMangaSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext

@Suppress("unused")
class TestMangaRepository(
	private val loaderContext: MangaLoaderContext,
	cache: MemoryContentCache
) : EmptyMangaRepository(TestMangaSource)
