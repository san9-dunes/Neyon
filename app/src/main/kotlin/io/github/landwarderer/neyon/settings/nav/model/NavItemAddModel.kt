package io.github.landwarderer.neyon.settings.nav.model

import io.github.landwarderer.neyon.list.ui.model.ListModel

data class NavItemAddModel(
	val canAdd: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is NavItemAddModel
}
