package io.github.landwarderer.neyon.mihon.parsers.util

import okhttp3.HttpUrl
import io.github.landwarderer.neyon.mihon.parsers.model.Content
import io.github.landwarderer.neyon.mihon.parsers.model.ContentSource

public interface LinkResolver {
    public val link: HttpUrl
    public suspend fun getSource(): ContentSource?
    public suspend fun getContent(): Content?
}

