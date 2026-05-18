## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - SQLite Variable Limit in Room
**Learning:** When using bulk operations with `IN (:ids)` on Android, SQLite imposes a hard limit of 999 variables per query on older devices (API < 30). Passing a large list directly to DAO methods like `deleteAll(ids)` or `clearCounters(ids)` can cause a `SQLiteException` if the list exceeds this size.
**Action:** Always wrap bulk deletion or update operations using the `IN` clause with `.chunked(900).forEach { ... }` to guarantee safety across all supported Android versions, or let Room handle collection splitting by default if running Room >= 2.4.0 (but explicit chunking ensures deterministic safety during batch processing).
