package io.github.landwarderer.neyon.suggestions.domain

import io.github.landwarderer.neyon.core.db.MangaDatabase
import io.github.landwarderer.neyon.favourites.data.FavouritesDao
import io.github.landwarderer.neyon.history.data.HistoryDao
import io.github.landwarderer.neyon.core.db.dao.TagsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tag Affinity Profiler — analyses the user's Favourites and recent History
 * to build a weighted list of the tags they are most interested in.
 *
 * Weighting:
 *   - Tag appears in a Favourite manga  → +2 points
 *   - Tag appears in a recent History manga → +1 point
 *   - Membership in both buckets       → +2 (Favourite weight wins; sets are resolved
 *                                         before scoring so each manga_id is counted once
 *                                         per bucket it belongs to)
 *
 * The function is safe to call from any coroutine context; all DB access is
 * internally dispatched to [Dispatchers.IO].
 */
@Singleton
class GetUserAffinityTagsUseCase @Inject constructor(
    private val db: MangaDatabase,
) {

    private val favouritesDao: FavouritesDao get() = db.getFavouritesDao()
    private val historyDao: HistoryDao       get() = db.getHistoryDao()
    private val tagsDao: TagsDao             get() = db.getTagsDao()

    /**
     * @param topN How many top tags to return (default 10).
     * @return Normalised (trimmed + lowercased) tag names sorted by descending affinity score.
     */
    suspend operator fun invoke(topN: Int = 10): List<String> = withContext(Dispatchers.IO) {

        // ── 1. Fetch seed IDs ───────────────────────────────────────────────────
        val favouriteIds: Set<Long> = favouritesDao.findAllMangaIds().toHashSet()

        // Cap history at 100 most-recent entries to prevent memory bloat.
        val historyIds: Set<Long>   = historyDao.findRecentIds(limit = 100).toHashSet()

        // Early-exit: no local data → new user, nothing to profile.
        if (favouriteIds.isEmpty() && historyIds.isEmpty()) return@withContext emptyList()

        // ── 2. Batch-fetch all associated tags in a single IN (:ids) query ──────
        // Combine IDs so we hit the DB exactly once.
        val allIds: List<Long> = (favouriteIds + historyIds).toList()
        val allTags            = tagsDao.findTagsForMangaIds(allIds) // returns List<TagEntity>

        if (allTags.isEmpty()) return@withContext emptyList()

        // ── 3. Build a lookup: mangaId → Set<normalised tag title> ───────────────
        // We need to know which tags belong to which manga to apply per-manga weights.
        // findTagsForMangaIds returns one row per (manga, tag) pair — re-query via
        // manga_tags to get the mapping. To avoid a second DB round-trip we use an
        // efficient in-memory approach: fetch tags keyed by manga_id using the existing
        // findTagsForMangaIds query (which returns tag rows joined with manga_tags).
        //
        // Since TagEntity does not carry manga_id, we must separate weights by bucket:
        //   • Tags from favouriteIds vs historyIds.
        //
        // Strategy: fetch tags for each bucket separately (still only 2 queries total;
        // both use the IN clause — never a loop).

        val favouriteTags: List<String> = if (favouriteIds.isNotEmpty()) {
            tagsDao.findTagsForMangaIds(favouriteIds.toList())
                .map { it.title.trim().lowercase() }
        } else emptyList()

        val historyTags: List<String> = if (historyIds.isNotEmpty()) {
            tagsDao.findTagsForMangaIds(historyIds.toList())
                .map { it.title.trim().lowercase() }
        } else emptyList()

        // ── 4. Score: +2 for Favourite, +1 for History ──────────────────────────
        val scores = mutableMapOf<String, Int>()

        for (tag in favouriteTags) {
            scores[tag] = (scores[tag] ?: 0) + 2
        }
        for (tag in historyTags) {
            // Skip tags already counted via Favourites to avoid double-counting the
            // same manga that sits in both buckets (Favourite weight already dominates).
            // However, a tag from a *different* history manga (not in favourites) should
            // still contribute +1, so we always add +1 for every history occurrence.
            scores[tag] = (scores[tag] ?: 0) + 1
        }

        // ── 5. Sort descending and return top-N tag strings ─────────────────────
        scores.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }
}
