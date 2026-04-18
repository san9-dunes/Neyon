package io.github.landwarderer.neyon.reader.domain

import androidx.collection.LongSparseArray
import androidx.collection.contains
import io.github.landwarderer.neyon.reader.ui.pager.ReaderPage

class ChapterPages private constructor(private val pages: ArrayDeque<ReaderPage>) : List<ReaderPage> by pages {

	// map chapterId to index in pages deque
	private val indices = LongSparseArray<IntRange>()

	constructor() : this(ArrayDeque())

	val chaptersSize: Int
		get() = indices.size()

	@Synchronized
	fun removeFirst() {
		val chapterId = pages.first().chapterId
		indices.remove(chapterId)
		var delta = 0
		while (pages.first().chapterId == chapterId) {
			pages.removeFirst()
			delta--
		}
		shiftIndices(delta)
	}

	@Synchronized
	fun removeLast() {
		val chapterId = pages.last().chapterId
		indices.remove(chapterId)
		// NOTE: Removing from the tail does not affect earlier page indices, so no shiftIndices() call
		// is needed here — unlike removeFirst() which shifts everything down.
		while (pages.last().chapterId == chapterId) {
			pages.removeLast()
		}
	}

	@Synchronized
	fun addLast(id: Long, newPages: List<ReaderPage>): Boolean {
		if (id in indices) {
			return false
		}
		indices.put(id, pages.size until (pages.size + newPages.size))
		pages.addAll(newPages)
		return true
	}

	@Synchronized
	fun addFirst(id: Long, newPages: List<ReaderPage>): Boolean {
		if (id in indices) {
			return false
		}
		shiftIndices(newPages.size)
		indices.put(id, newPages.indices)
		pages.addAll(0, newPages)
		return true
	}

	@Synchronized
	fun clear() {
		indices.clear()
		pages.clear()
	}

	@Synchronized
	fun size(id: Long) = indices[id]?.run {
		endInclusive - start + 1
	} ?: 0

	@Synchronized
	fun subList(id: Long): List<ReaderPage> {
		val range = indices[id] ?: return emptyList()
		// Return a copy so callers don’t hold a live view into the mutable deque
		return ArrayList(pages.subList(range.first, range.last + 1))
	}

	operator fun contains(chapterId: Long) = chapterId in indices

	private fun shiftIndices(delta: Int) {
		for (i in 0 until indices.size()) {
			val range = indices.valueAt(i)
			indices.setValueAt(i, range + delta)
		}
	}

	private operator fun IntRange.plus(delta: Int): IntRange {
		return IntRange(start + delta, endInclusive + delta)
	}
}
