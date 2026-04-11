package io.github.landwarderer.neyon.core.exceptions

class SyncApiException(
	message: String,
	val code: Int,
) : RuntimeException(message)
