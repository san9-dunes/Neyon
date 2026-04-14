package io.github.landwarderer.neyon.suggestions.domain

import io.github.landwarderer.neyon.core.db.dao.TagsDao
import io.github.landwarderer.neyon.favourites.data.FavouritesDao
import io.github.landwarderer.neyon.history.data.HistoryDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetUserAffinityTagsUseCase @Inject constructor(
    private val favouritesDao: FavouritesDao,
    private val historyDao: HistoryDao,
    private val tagsDao: TagsDao
) {
    suspend operator fun invoke(): List<String> {
        // Query the local Room Database to get the IDs of the user's favorited manga and/or recently read history manga.
        val favouriteMangaIds = favouritesDao.findAll().map { it.manga.id }
        val historyMangaIds = historyDao.findAll(0, 100).map { it.manga.id } // Assume top 100 history entries is logical
        
        val targetMangaIds = (favouriteMangaIds + historyMangaIds).distinct()
        if (targetMangaIds.isEmpty()) return emptyList()

        // Use TagsDao to fetch the TagEntity or MangaTagsEntity associated with those specific Manga IDs.
        val tags = tagsDao.findTagsForMangaIds(targetMangaIds)

        // Group the retrieved tags by their name/title, and count the occurrences
        val tagCounts = tags.groupingBy { it.title.lowercase() }.eachCount()

        // Sort descending by count and return the top 10 most frequent tags
        return tagCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
}
