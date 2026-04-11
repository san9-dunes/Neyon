package io.github.landwarderer.neyon.history.ui

import android.content.Context
import io.github.landwarderer.neyon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.neyon.list.ui.adapter.MangaListAdapter
import io.github.landwarderer.neyon.list.ui.adapter.MangaListListener
import io.github.landwarderer.neyon.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
