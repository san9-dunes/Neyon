package io.github.landwarderer.neyon.search.ui.suggestion.adapter

import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.search.ui.suggestion.SearchSuggestionListener
import io.github.landwarderer.neyon.search.ui.suggestion.model.SearchSuggestionItem

const val SEARCH_SUGGESTION_ITEM_TYPE_QUERY = 0

class SearchSuggestionAdapter(
	listener: SearchSuggestionListener,
) : BaseListAdapter<SearchSuggestionItem>() {

	init {
		delegatesManager
			.addDelegate(SEARCH_SUGGESTION_ITEM_TYPE_QUERY, searchSuggestionQueryAD(listener))
			.addDelegate(searchSuggestionSourceAD(listener))
			.addDelegate(searchSuggestionSourceTipAD(listener))
			.addDelegate(searchSuggestionTagsAD(listener))
			.addDelegate(searchSuggestionMangaListAD(listener))
			.addDelegate(searchSuggestionQueryHintAD(listener))
			.addDelegate(searchSuggestionAuthorAD(listener))
			.addDelegate(searchSuggestionTextAD())
	}
}
