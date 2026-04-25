package io.github.landwarderer.neyon.mihon.parsers.core

import io.github.landwarderer.neyon.mihon.parsers.ContentLoaderContext
import io.github.landwarderer.neyon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.neyon.mihon.parsers.model.Content
import io.github.landwarderer.neyon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.neyon.mihon.parsers.model.ContentSource
import io.github.landwarderer.neyon.mihon.parsers.model.SortOrder

@InternalParsersApi
public abstract class SinglePageContentParser(
	context: ContentLoaderContext,
	source: ContentSource,
) : AbstractContentParser(context, source) {

	final override suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	public abstract suspend fun getList(order: SortOrder, filter: ContentListFilter): List<Content>
}

