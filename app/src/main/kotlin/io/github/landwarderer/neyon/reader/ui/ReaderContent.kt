package io.github.landwarderer.neyon.reader.ui

import io.github.landwarderer.neyon.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)