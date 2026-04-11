package io.github.landwarderer.neyon.list.ui.adapter

import android.view.View
import io.github.landwarderer.neyon.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
