## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Operations for Background Tasks
**Learning:** Found an N+1 query problem in `TrackingRepository.updateTracks` where un-tracked chapters were individually inserted in a for-loop and then removed unused ones via single deletions. Room processes these much faster as bulk upserts (`upsertAll(newTracks)`) and bulk deletes (`deleteAll(ids)`).
**Action:** Always refactor iterative DAO `upsert`/`delete` calls that execute inside a transaction into bulk operations for better performance.

## 2024-05-24 - Bulk Delete SQLite Variable Limits
**Learning:** Found that using a bulk delete with an `IN (:mangaIds)` clause (e.g. `deleteAll(ids)`) can crash on older Android devices (SQLite < 3.32.0) if the collection size exceeds 999 due to `SQLITE_MAX_VARIABLE_NUMBER`.
**Action:** When performing bulk queries with `IN` clauses via Room, always split the collection into safe chunks (e.g., `ids.chunked(900).forEach { ... }`) to prevent runtime exceptions.
