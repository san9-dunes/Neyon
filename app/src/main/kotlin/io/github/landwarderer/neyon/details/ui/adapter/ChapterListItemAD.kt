package io.github.landwarderer.neyon.details.ui.adapter

import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.ui.list.AdapterDelegateClickListenerAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.util.ext.drawableStart
import io.github.landwarderer.neyon.core.util.ext.getThemeColorStateList
import io.github.landwarderer.neyon.core.util.ext.textAndVisible
import io.github.landwarderer.neyon.databinding.ItemChapterBinding
import io.github.landwarderer.neyon.details.ui.model.ChapterListItem
import io.github.landwarderer.neyon.list.ui.model.ListModel
import com.google.android.material.R as materialR

fun chapterListItemAD(
	clickListener: OnListItemClickListener<ChapterListItem>,
	onDownloadClick: ((ChapterListItem) -> Unit)? = null,
) = adapterDelegateViewBinding<ChapterListItem, ListModel, ItemChapterBinding>(
	viewBinding = { inflater, parent -> ItemChapterBinding.inflate(inflater, parent, false) },
	on = { item, _, _ -> item is ChapterListItem && !item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	
	binding.buttonDownload.setOnClickListener {
		onDownloadClick?.invoke(item)
	}

	bind {
		binding.textViewTitle.text = item.getTitle(context.resources)
		binding.textViewDescription.textAndVisible = item.description
		when {
			item.isCurrent -> {
				binding.textViewTitle.drawableStart = ContextCompat.getDrawable(context, R.drawable.ic_current_chapter)
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorPrimary))
				binding.textViewDescription.setTextColor(context.getThemeColorStateList(android.R.attr.textColorPrimary))
				binding.textViewTitle.typeface = Typeface.DEFAULT_BOLD
				binding.textViewDescription.typeface = Typeface.DEFAULT_BOLD
			}

			item.isUnread -> {
				binding.textViewTitle.drawableStart = if (item.isNew) {
					ContextCompat.getDrawable(context, R.drawable.ic_new)
				} else {
					null
				}
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorPrimary))
				binding.textViewDescription.setTextColor(context.getThemeColorStateList(materialR.attr.colorOutline))
				binding.textViewTitle.typeface = Typeface.DEFAULT
				binding.textViewDescription.typeface = Typeface.DEFAULT
			}

			else -> {
				binding.textViewTitle.drawableStart = null
				binding.textViewTitle.setTextColor(context.getThemeColorStateList(android.R.attr.textColorHint))
				binding.textViewDescription.setTextColor(context.getThemeColorStateList(android.R.attr.textColorHint))
				binding.textViewTitle.typeface = Typeface.DEFAULT
				binding.textViewDescription.typeface = Typeface.DEFAULT
			}
		}
		binding.imageViewBookmarked.isVisible = item.isBookmarked

		val btn = binding.buttonDownload
		when {
			item.isDownloaded -> {
				btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.COMPLETED
			}
			item.isDownloadPaused -> {
				btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.PENDING
			}
			item.downloadPercent != null -> {
				btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.ACTIVE
				btn.progress = item.downloadPercent ?: 0f
			}
			else -> {
				btn.state = io.github.landwarderer.neyon.core.ui.widget.DownloadButton.State.DEFAULT
			}
		}
	}
}
