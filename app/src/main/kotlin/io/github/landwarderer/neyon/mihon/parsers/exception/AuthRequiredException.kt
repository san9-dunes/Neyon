package io.github.landwarderer.neyon.mihon.parsers.exception

import io.github.landwarderer.neyon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.neyon.mihon.parsers.model.ContentSource
import okio.IOException

/**
 * Authorization is required for access to the requested content
 */
public class AuthRequiredException @InternalParsersApi @JvmOverloads constructor(
	public val source: ContentSource,
	cause: Throwable? = null,
) : IOException("Authorization required", cause)

