package io.github.landwarderer.neyon.details.ui.adapter

import android.graphics.Typeface
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.core.ui.list.AdapterDelegateClickListenerAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.util.ext.getThemeColorStateList
import io.github.landwarderer.neyon.core.util.ext.setTooltipCompat
import io.github.landwarderer.neyon.databinding.ItemChapterGridBinding
import io.github.landwarderer.neyon.details.ui.model.ChapterListItem
import io.github.landwarderer.neyon.list.ui.model.ListModel

fun chapterGridItemAD(
	clickListener: OnListItemClickListener<ChapterListItem>,
	onDownloadClick: ((ChapterListItem) -> Unit)? = null,
) = adapterDelegateViewBinding<ChapterListItem, ListModel, ItemChapterGridBinding>(
	viewBinding = { inflater, parent -> ItemChapterGridBinding.inflate(inflater, parent, false) },
	on = { item, _, _ -> item is ChapterListItem && item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	
	binding.buttonDownload.setOnClickListener {
		onDownloadClick?.invoke(item)
	}

	bind { payloads ->
		if (payloads.isEmpty()) {
			binding.textViewTitle.text = item.chapter.numberString() ?: "?"
			itemView.setTooltipCompat(item.chapter.title)
		}
		binding.imageViewNew.isVisible = item.isNew
		binding.imageViewCurrent.isVisible = item.isCurrent
		binding.imageViewBookmarked.isVisible = item.isBookmarked
		
		val btn = binding.buttonDownload
		btn.isVisible = item.isDownloaded || item.isDownloadPaused || item.downloadPercent != null
		
		when {
			item.isDownloaded -> btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.COMPLETED
			item.isDownloadPaused -> btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.PENDING
			item.downloadPercent != null -> {
				btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.ACTIVE
				btn.progress = item.downloadPercent
			}
			else -> btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.DEFAULT
		}

		when {
			item.isCurrent -> {
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorPrimary))
				binding.textViewTitle.typeface = Typeface.DEFAULT_BOLD
			}

			item.isUnread -> {
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorPrimary))
				binding.textViewTitle.typeface = Typeface.DEFAULT
			}

			else -> {
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorHint))
				binding.textViewTitle.typeface = Typeface.DEFAULT
			}
		}
	}
}

