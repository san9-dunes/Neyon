## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Operations in Repository layer
**Learning:** Found an N+1 query issue in `TrackingRepository.updateTracks` where IDs mapped to entities were being upserted and deleted iteratively in a `for` loop. Adding bulk `upsertAll(entities: Collection)` and `deleteAll(ids: Collection)` methods in the DAO resolves this, allowing Room/SQLite to process these much faster.
**Action:** When working with collections and `for` loops in repositories, check the DAO to see if it provides or can be updated to provide bulk operations using `Collection` parameters for `@Upsert`, `@Insert`, or `@Query` with `IN` clauses to replace iterative calls.
