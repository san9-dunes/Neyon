package io.github.landwarderer.neyon.details.ui.adapter

import android.content.Context
import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.neyon.details.ui.model.ChapterListItem
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.adapter.listHeaderAD
import io.github.landwarderer.neyon.list.ui.model.ListHeader
import io.github.landwarderer.neyon.list.ui.model.ListModel

class ChaptersAdapter(
	onItemClickListener: OnListItemClickListener<ChapterListItem>,
	onDownloadClick: ((ChapterListItem) -> Unit)? = null,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	private var hasVolumes = false

	init {
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
		addDelegate(ListItemType.CHAPTER_LIST, chapterListItemAD(onItemClickListener, onDownloadClick))
		addDelegate(ListItemType.CHAPTER_GRID, chapterGridItemAD(onItemClickListener, onDownloadClick))
	}

	override suspend fun emit(value: List<ListModel>?) {
		super.emit(value)
		hasVolumes = value != null && value.any { it is ListHeader }
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return if (hasVolumes) {
			findHeader(position)?.getText(context)
		} else {
			val chapter = (items.getOrNull(position) as? ChapterListItem)?.chapter ?: return null
			chapter.numberString()
		}
	}
}
