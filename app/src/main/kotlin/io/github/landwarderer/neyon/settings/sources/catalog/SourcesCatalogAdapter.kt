package io.github.landwarderer.neyon.settings.sources.catalog

import android.content.Context
import io.github.landwarderer.neyon.core.model.getTitle
import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.adapter.loadingStateAD
import io.github.landwarderer.neyon.list.ui.model.ListModel

class SourcesCatalogAdapter(
	listener: OnListItemClickListener<SourceCatalogItem.Source>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.CHAPTER_LIST, sourceCatalogItemSourceAD(listener))
		addDelegate(ListItemType.HINT_EMPTY, sourceCatalogItemHintAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? SourceCatalogItem.Source)?.source?.getTitle(context)?.take(1)
	}
}
