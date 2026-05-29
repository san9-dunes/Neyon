## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Delete and Update Optimization in TracksDao
**Learning:** Found N+1 operations in `TrackingRepository.clearUpdates` and `TrackingRepository.updateTracks` where counters were cleared, entities upserted, and entities deleted one by one in loops within `db.withTransaction`. Room can process these much faster as bulk operations using `IN (:mangaIds)` for queries and `@Upsert` with Collections.
**Action:** Always verify if iterative DAO `delete` or `update` calls within transactions can be replaced with bulk query methods using `IN` clauses or bulk `@Upsert` for better performance.
