package io.github.landwarderer.neyon.mihon.model

import io.github.landwarderer.neyon.mihon.parsers.model.ContentSource

data class ContentSourceInfo(
    val mangaSource: ContentSource,
    val isEnabled: Boolean,
    val isPinned: Boolean,
) : ContentSource by mangaSource
