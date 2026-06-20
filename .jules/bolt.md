## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-11-20 - Bulk Insert Optimization for HistoryDao
**Learning:** The HistoryDao had an unoptimized loop `entities.forEach { if (update(e) == 0) insert(e) }` for batch upserts. Room has built-in `@Upsert` capabilities that can handle collections much more efficiently inside a single operation. Replaced with `@Upsert abstract suspend fun upsert(entities: Iterable<HistoryEntity>)` resolving an N+1 query problem during bulk operations.
**Action:** When finding iterative calls like `update` and `insert` for every item in an iterable, look to use Room 2.5+'s `@Upsert` functionality for massive performance gains.
