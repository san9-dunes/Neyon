package io.github.landwarderer.neyon.favourites.ui.categories.select.adapter

import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.favourites.ui.categories.select.model.MangaCategoryItem
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.adapter.emptyStateListAD
import io.github.landwarderer.neyon.list.ui.adapter.loadingStateAD
import io.github.landwarderer.neyon.list.ui.model.ListModel

class MangaCategoriesAdapter(
	clickListener: OnListItemClickListener<MangaCategoryItem>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, mangaCategoryAD(clickListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}
}
