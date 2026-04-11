package io.github.landwarderer.neyon.details.domain

import io.github.landwarderer.neyon.core.util.LocaleStringComparator
import io.github.landwarderer.neyon.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
