@file:JvmName("ContentParsersUtils")

package io.github.landwarderer.neyon.mihon.parsers.util

import io.github.landwarderer.neyon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.neyon.mihon.parsers.model.ContentListFilter
import kotlin.contracts.contract

fun ContentListFilter?.isNullOrEmpty(): Boolean {
	contract {
		returns(false) implies (this@isNullOrEmpty != null)
	}
	return this == null || this.isEmpty()
}

fun Collection<ContentChapter>.findById(chapterId: Long): ContentChapter? = find { x ->
	x.id == chapterId
}
