package io.github.landwarderer.neyon.core.model

import io.github.landwarderer.neyon.core.ui.widgets.ChipsView
import io.github.landwarderer.neyon.list.domain.ListFilterOption

fun ListFilterOption.toChipModel(isChecked: Boolean) = ChipsView.ChipModel(
	title = titleText,
	titleResId = titleResId,
	icon = iconResId,
	iconData = getIconData(),
	isChecked = isChecked,
	counter = if (this is ListFilterOption.Branch) chaptersCount else 0,
	data = this,
)
