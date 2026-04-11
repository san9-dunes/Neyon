package io.github.landwarderer.neyon.reader.ui

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenPagesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hidden_pages", Context.MODE_PRIVATE)

    fun getHiddenPages(chapterId: Long): Set<String> {
        return prefs.getStringSet(chapterId.toString(), emptySet()) ?: emptySet()
    }

    fun addHiddenPage(chapterId: Long, pageUrl: String) {
        val hidden = getHiddenPages(chapterId).toMutableSet()
        hidden.add(pageUrl)
        prefs.edit().putStringSet(chapterId.toString(), hidden).apply()
    }
    
    fun removeHiddenPage(chapterId: Long, pageUrl: String) {
        val hidden = getHiddenPages(chapterId).toMutableSet()
        hidden.remove(pageUrl)
        prefs.edit().putStringSet(chapterId.toString(), hidden).apply()
    }
}
