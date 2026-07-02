## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Operations for Room in updateTracks
**Learning:** Background sync tasks, such as `updateTracks()`, can severely bottleneck due to N+1 queries during massive `upsert` and `delete` loop calls over Room DAOs.
**Action:** Replace iterative Room query executions with bulk Collection inputs (like `@Upsert(entities: Collection)`) and chunked `IN (:ids)` SQL queries to perform operations safely within transaction bounds.
