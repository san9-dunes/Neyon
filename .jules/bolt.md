## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-25 - Bulk Delete Optimization with SQLite Limits in Room
**Learning:** When resolving N+1 query loops using bulk `IN (:ids)` queries in Room, passing collections larger than 999 items causes an `SQLiteException` due to host parameter limits on older Android versions.
**Action:** Always chunk large collections (e.g., `ids.chunked(900).forEach { dao.delete(it) }`) when optimizing loops into bulk `DELETE` or `SELECT` operations using an `IN` clause.
