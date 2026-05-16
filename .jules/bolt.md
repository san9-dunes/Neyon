## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - TrackingRepository Bulk Clear Optimization
**Learning:** Found an N+1 query pattern where tracking updates were cleared iteratively in `TrackingRepository.clearUpdates` using a loop inside a transaction. Room supports bulk updates with an `IN` clause.
**Action:** Replace iterative DAO update calls with a single method accepting a `Collection` for better performance and reduced transaction overhead.
