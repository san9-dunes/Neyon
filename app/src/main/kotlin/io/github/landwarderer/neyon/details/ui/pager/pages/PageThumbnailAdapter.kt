package io.github.landwarderer.neyon.details.ui.pager.pages

import android.content.Context
import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.adapter.listHeaderAD
import io.github.landwarderer.neyon.list.ui.model.ListModel

class PageThumbnailAdapter(
	clickListener: OnListItemClickListener<PageThumbnail>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.PAGE_THUMB, pageThumbnailAD(clickListener))
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
