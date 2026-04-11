package io.github.landwarderer.neyon.core.exceptions

import okio.IOException

class WrapperIOException(override val cause: Exception) : IOException(cause)
