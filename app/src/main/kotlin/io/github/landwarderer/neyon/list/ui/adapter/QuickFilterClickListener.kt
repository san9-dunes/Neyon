package io.github.landwarderer.neyon.list.ui.adapter

import io.github.landwarderer.neyon.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)

	fun onFilterOptionCloseClick(option: ListFilterOption) {}
}
