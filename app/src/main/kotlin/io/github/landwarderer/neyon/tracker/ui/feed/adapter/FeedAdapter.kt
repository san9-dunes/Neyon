package io.github.landwarderer.neyon.tracker.ui.feed.adapter

import android.content.Context
import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.adapter.MangaListListener
import io.github.landwarderer.neyon.list.ui.adapter.emptyStateListAD
import io.github.landwarderer.neyon.list.ui.adapter.errorFooterAD
import io.github.landwarderer.neyon.list.ui.adapter.errorStateListAD
import io.github.landwarderer.neyon.list.ui.adapter.listHeaderAD
import io.github.landwarderer.neyon.list.ui.adapter.loadingFooterAD
import io.github.landwarderer.neyon.list.ui.adapter.loadingStateAD
import io.github.landwarderer.neyon.list.ui.adapter.quickFilterAD
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.size.ItemSizeResolver
import io.github.landwarderer.neyon.tracker.ui.feed.model.FeedItem

class FeedAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
	feedClickListener: OnListItemClickListener<FeedItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.FEED, feedItemAD(feedClickListener))
		addDelegate(
			ListItemType.MANGA_NESTED_GROUP,
			updatedMangaAD(
				sizeResolver = sizeResolver,
				listener = listener,
				headerClickListener = listener,
			),
		)
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(listener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.QUICK_FILTER, quickFilterAD(listener))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
