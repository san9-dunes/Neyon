package io.github.landwarderer.neyon.alternatives.ui

import io.github.landwarderer.neyon.list.ui.ListModelDiffCallback
import io.github.landwarderer.neyon.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * A per-source group shown in the alternatives list.
 * Mirrors [io.github.landwarderer.neyon.search.ui.multi.SearchResultsListModel] semantics:
 * - [loading] = true while the source is still being searched
 * - [error] != null if the source errored
 * - [items] = results found so far (may be empty even when done)
 */
data class AlternativeSourceModel(
	val source: MangaSource,
	val items: List<MangaAlternativeModel>,
	val error: Throwable?,
	val loading: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean =
		other is AlternativeSourceModel && source == other.source

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is AlternativeSourceModel && previousState.items != items) {
			ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}
