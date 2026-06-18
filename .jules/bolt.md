## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-25 - Bulk Insert Optimization in Suggestions
**Learning:** Found another N+1 insertion problem in `SuggestionRepository.replace` where suggestions were inserted iteratively in a `.forEach` loop. This leads to substantial SQLite transaction overhead.
**Action:** Replaced iterative loops calling `insert`/`upsert` in DAOs with bulk operations, using collections and mapped tags to deduplicate prior to the bulk upsert, eliminating the N+1 problem and significantly speeding up the batch insertion process.
