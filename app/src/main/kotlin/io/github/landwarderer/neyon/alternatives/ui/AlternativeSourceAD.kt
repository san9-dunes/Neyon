package io.github.landwarderer.neyon.alternatives.ui

import android.annotation.SuppressLint
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.core.model.getTitle
import io.github.landwarderer.neyon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.neyon.core.util.ext.getDisplayMessage
import io.github.landwarderer.neyon.core.util.ext.textAndVisible
import io.github.landwarderer.neyon.databinding.ItemAlternativeSourceGroupBinding
import io.github.landwarderer.neyon.list.ui.model.ListModel

/**
 * Adapter delegate for [AlternativeSourceModel].
 *
 * Renders a collapsible per-source row with:
 *  - Source name as a header
 *  - An inline progress spinner while the source is loading (mirrors search UI)
 *  - An error message if the source failed
 *  - A vertical nested RecyclerView of [MangaAlternativeModel] cards (chapters diff, migrate)
 */
@SuppressLint("NotifyDataSetChanged")
fun alternativeSourceAD(
	sharedPool: RecyclerView.RecycledViewPool,
	coil: ImageLoader,
	lifecycleOwner: LifecycleOwner,
	itemClickListener: OnListItemClickListener<MangaAlternativeModel>,
) = adapterDelegateViewBinding<AlternativeSourceModel, ListModel, ItemAlternativeSourceGroupBinding>(
	{ layoutInflater, parent -> ItemAlternativeSourceGroupBinding.inflate(layoutInflater, parent, false) },
) {
	binding.recyclerView.setRecycledViewPool(sharedPool)
	val nestedAdapter = ListDelegationAdapter(alternativeAD(coil, lifecycleOwner, itemClickListener))
	binding.recyclerView.adapter = nestedAdapter

	bind { _ ->
		binding.textViewTitle.text = item.source.getTitle(context)
		nestedAdapter.items = item.items
		nestedAdapter.notifyDataSetChanged()
		binding.recyclerView.isGone = item.items.isEmpty()
		binding.textViewError.textAndVisible = item.error?.getDisplayMessage(context.resources)
		binding.progressBar.isVisible = item.loading
	}
}
