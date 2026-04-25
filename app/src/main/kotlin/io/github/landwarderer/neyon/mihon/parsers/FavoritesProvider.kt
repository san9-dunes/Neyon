package io.github.landwarderer.neyon.mihon.parsers

import io.github.landwarderer.neyon.mihon.parsers.model.Content

interface FavoritesProvider {

    suspend fun fetchFavorites(): List<Content>
}
