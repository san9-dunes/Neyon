package io.github.landwarderer.neyon.download.domain

data class DownloadProgress(
	val totalChapters: Int,
	val currentChapter: Int,
	val currentChapterId: Long,
	val totalPages: Int,
	val currentPage: Int,
)
