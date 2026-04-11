package io.github.landwarderer.neyon.list.ui.adapter

import android.view.View
import io.github.landwarderer.neyon.core.ui.widgets.TipView

interface MangaListListener : MangaDetailsClickListener, ListStateHolderListener, ListHeaderClickListener,
	TipView.OnButtonClickListener, QuickFilterClickListener {

	fun onFilterClick(view: View?)
}
