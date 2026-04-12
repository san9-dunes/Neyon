package io.github.landwarderer.neyon.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.core.ui.list.AdapterDelegateClickListenerAdapter
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.util.ext.setTooltipCompat
import io.github.landwarderer.neyon.core.util.ext.textAndVisible
import io.github.landwarderer.neyon.databinding.ItemMangaListBinding
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.list.ui.model.MangaCompactListModel
import io.github.landwarderer.neyon.list.ui.model.MangaListModel

fun mangaListItemAD(
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaCompactListModel, ListModel, ItemMangaListBinding>(
	{ inflater, parent -> ItemMangaListBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewSubtitle.textAndVisible = item.subtitle
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
