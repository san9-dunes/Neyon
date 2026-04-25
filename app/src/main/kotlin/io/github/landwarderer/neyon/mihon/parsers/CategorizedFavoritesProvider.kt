package io.github.landwarderer.neyon.mihon.parsers

import io.github.landwarderer.neyon.mihon.parsers.model.Content

interface CategorizedFavoritesProvider : FavoritesProvider {

    suspend fun fetchFavoriteFolders(): List<ContentFavoriteFolder>

    suspend fun fetchFavorites(folderId: String): List<Content>

    override suspend fun fetchFavorites(): List<Content> {
        return fetchFavoriteFolders().flatMap { fetchFavorites(it.id) }.distinctBy { it.url }
    }
}

data class ContentFavoriteFolder(
    val id: String,
    val title: String,
    val count: Int? = null,
)
