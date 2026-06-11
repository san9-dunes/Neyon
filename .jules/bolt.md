## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-03-24 - Bulk Insert Optimization for Suggestions
**Learning:** Room DB operations inside an iterative `.forEach` loop across multiple entities (e.g., `Manga`, `Tags`, `Suggestions`) inside a `withTransaction` block creates significant N+1 query overhead. Each loop iteration incurs an independent SQLite transaction lock despite being wrapped in an overall transaction.
**Action:** When updating or replacing a collection of relations or suggestions, resolve the lists first, use `distinctBy` for relations like Tags to prevent constraint exceptions, and use a bulk `@Upsert` / `@Insert` across a single collected list of entities to drastically lower SQLite transaction lock time and N+1 query overhead.
