package io.github.landwarderer.neyon.mihon.parsers

import io.github.landwarderer.neyon.mihon.parsers.model.Content

interface FavoritesSyncProvider {

    suspend fun addFavorite(manga: Content): Boolean

    suspend fun removeFavorite(manga: Content): Boolean
}
