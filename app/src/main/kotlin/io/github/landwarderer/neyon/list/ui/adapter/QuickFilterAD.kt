package io.github.landwarderer.neyon.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.core.ui.widgets.ChipsView
import io.github.landwarderer.neyon.databinding.ItemQuickFilterBinding
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.QuickFilter

fun quickFilterAD(
	listener: QuickFilterClickListener,
) = adapterDelegateViewBinding<QuickFilter, ListModel, ItemQuickFilterBinding>(
	{ layoutInflater, parent -> ItemQuickFilterBinding.inflate(layoutInflater, parent, false) }
) {

	binding.chipsTags.onChipClickListener = ChipsView.OnChipClickListener { chip, data ->
		if (data is ListFilterOption) {
			listener.onFilterOptionClick(data)
		}
	}
	binding.chipsTags.onChipCloseClickListener = ChipsView.OnChipCloseClickListener { chip, data ->
		if (data is ListFilterOption) {
			listener.onFilterOptionCloseClick(data)
		}
	}

	bind { payloads ->
		binding.chipsTags.setChips(item.items)
	}
}
