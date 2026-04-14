package io.github.landwarderer.neyon.list.domain

interface QuickFilterListener {

	fun setFilterOption(option: ListFilterOption, isApplied: Boolean)

	fun toggleFilterOption(option: ListFilterOption)

	fun removeFilterOption(option: ListFilterOption) {}

	fun clearFilter()
}
