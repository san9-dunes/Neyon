## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-06-24 - Bulk Upsert Optimization in Room
**Learning:** Found an N+1 database operations problem in `HistoryDao.upsert(Iterable<HistoryEntity>)` where history entries were updated or inserted iteratively in a loop (`if (update(e) == 0) insert(e)`). Room provides an `@Upsert` annotation which handles collections efficiently and drastically reduces SQLite transaction overhead.
**Action:** Replaced iterative loop checking with a single `@Upsert` annotated abstract function taking `Iterable<HistoryEntity>` to utilize Room's built-in bulk processing.
