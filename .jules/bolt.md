## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-18 - Optimize HistoryDao bulk upsert
**Learning:** Room database operations mapped over collections with standard iteration (`for (e in entities) { if (update(e) == 0) insert(e) }`) trigger N+1 query patterns, creating immense overhead.
**Action:** Replace iterative Room operations over collections with a single `@Upsert` annotated method that accepts an `Iterable<Entity>` to resolve the N+1 bottleneck.
