package io.github.landwarderer.neyon.favourites.ui.categories.adapter

import io.github.landwarderer.neyon.favourites.domain.model.Cover
import io.github.landwarderer.neyon.list.ui.ListModelDiffCallback
import io.github.landwarderer.neyon.list.ui.model.ListModel

data class AllCategoriesListModel(
	val mangaCount: Int,
	val covers: List<Cover>,
	val isVisible: Boolean,
	val isActionsEnabled: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is AllCategoriesListModel
	}

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is AllCategoriesListModel -> super.getChangePayload(previousState)
		previousState.isVisible != isVisible -> ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		previousState.isActionsEnabled != isActionsEnabled -> ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		else -> super.getChangePayload(previousState)
	}
}
