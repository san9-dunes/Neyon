## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Delete Operations in Room
**Learning:** Found iterative deletion (`for (id in ids) dao.delete(id)`) in `TrackingRepository.updateTracks()` causing an N+1 issue.
**Action:** Always replace iterative SQLite deletion with collection-based IN clauses (`DELETE FROM table WHERE id IN (:ids)`), which works efficiently with modern Room implementations.
