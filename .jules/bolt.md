## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Upsert Optimization in SuggestionRepository
**Learning:** Found another N+1 insertion problem in `SuggestionRepository.replace` where suggestions, tags, and manga-tag relations were inserted iteratively inside a loop. This drastically impacts performance during bulk additions.
**Action:** Replaced iterative operations with batch mappings outside of the transaction, and bulk operations using `upsertAll` and a newly introduced `upsertAllWithTags` helper in `MangaDao`. When creating or dealing with multiple insertions, look for `forEach` wrapping a Room DAO call and replace it with bulk methods.
