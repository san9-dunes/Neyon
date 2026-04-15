package io.github.landwarderer.neyon.search.ui.suggestion.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.core.ui.widgets.ChipsView
import io.github.landwarderer.neyon.databinding.ItemSearchSuggestionTagsBinding
import org.koitharu.kotatsu.parsers.model.MangaTag
import io.github.landwarderer.neyon.search.ui.suggestion.SearchSuggestionListener
import io.github.landwarderer.neyon.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionTagsAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<SearchSuggestionItem.Tags, SearchSuggestionItem, ItemSearchSuggestionTagsBinding>(
	{ layoutInflater, parent -> ItemSearchSuggestionTagsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.chipsGenres.onChipClickListener = ChipsView.OnChipClickListener { _, data ->
		listener.onTagClick(data as? MangaTag ?: return@OnChipClickListener)
	}

	bind { payloads ->
		binding.chipsGenres.setChips(item.tags)
	}
}
