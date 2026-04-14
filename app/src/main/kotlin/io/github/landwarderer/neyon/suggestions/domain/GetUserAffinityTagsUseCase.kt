package io.github.landwarderer.neyon.suggestions.domain

import io.github.landwarderer.neyon.core.db.MangaDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetUserAffinityTagsUseCase @Inject constructor(
    private val db: MangaDatabase
) {
    suspend operator fun invoke(limit: Int = 10): List<String> {
        // Uses the existing findPopularTags() which JOINs manga_tags with history/favourites
        return db.getTagsDao().findPopularTags(limit).map { it.title.lowercase() }
    }
}
