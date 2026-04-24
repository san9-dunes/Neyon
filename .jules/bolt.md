## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-24 - Bulk Delete and Upsert in TrackingRepository
**Learning:** Found N+1 operations in `TrackingRepository.updateTracks` and `TrackingRepository.clearUpdates` where Room DAO calls (`insert`, `delete`, `update`) were looped over iteratively.
**Action:** Always replace iterative database operations in loops with Room's bulk array/collection functions (`upsertAll`, queries using `IN (...)`) to reduce IPC and transaction overhead. Room automatically chunks parameters to comply with SQLite limits.
