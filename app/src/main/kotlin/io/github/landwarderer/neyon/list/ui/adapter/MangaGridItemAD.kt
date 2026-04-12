package io.github.landwarderer.neyon.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.ui.list.AdapterDelegateClickListenerAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.util.ext.setTooltipCompat
import io.github.landwarderer.neyon.databinding.ItemMangaGridBinding
import io.github.landwarderer.neyon.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.MangaGridModel
import io.github.landwarderer.neyon.list.ui.model.MangaListModel
import io.github.landwarderer.neyon.list.ui.size.ItemSizeResolver

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaGridBinding>(
	{ inflater, parent -> ItemMangaGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0

		binding.buttonMigrate.isVisible = !item.isSourceAvailable
		binding.textViewUnavailable.isVisible = !item.isSourceAvailable
		binding.buttonMigrate.setOnClickListener {
			clickListener.onMigrationClick(item)
		}
	}
}
