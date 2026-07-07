## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-10-24 - Bulk Upsert Optimization in Room DAOs
**Learning:** Found an N+1 query problem in `HistoryDao.upsert(Iterable<HistoryEntity>)` where entities were iteratively updated and inserted in a `for` loop. Room natively supports the `@Upsert` annotation on collections, which eliminates the need for manual loops and reduces SQLite transaction overhead.
**Action:** Replace manual loop blocks for database operations (`if (update(e) == 0) insert(e)`) with a single abstract method annotated with Room's `@Upsert` to improve batch processing performance.
