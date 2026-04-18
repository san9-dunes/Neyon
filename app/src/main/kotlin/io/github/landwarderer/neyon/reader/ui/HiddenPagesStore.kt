package io.github.landwarderer.neyon.reader.ui

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks pages the user has manually hidden during the current reading session.
 *
 * This store is intentionally in-memory only — hiding a page is a transient action
 * for the current session. Persisting it would permanently suppress pages across restarts
 * without any UI to undo it. Call [clearAll] from Settings if persistent hiding is added later.
 */
@Singleton
class HiddenPagesStore @Inject constructor() {

    private val hiddenPages = HashMap<Long, MutableSet<String>>()

    @Synchronized
    fun getHiddenPages(chapterId: Long): Set<String> {
        return hiddenPages[chapterId]?.toSet() ?: emptySet()
    }

    @Synchronized
    fun addHiddenPage(chapterId: Long, pageUrl: String) {
        hiddenPages.getOrPut(chapterId) { mutableSetOf() }.add(pageUrl)
    }

    @Synchronized
    fun removeHiddenPage(chapterId: Long, pageUrl: String) {
        hiddenPages[chapterId]?.remove(pageUrl)
    }

    @Synchronized
    fun clearAll() {
        hiddenPages.clear()
    }
}

