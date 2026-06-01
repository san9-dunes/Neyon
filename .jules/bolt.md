## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-18 - [Optimize Room DB Iterative Upserts]
**Learning:** Room DB withTransaction blocks using iterative updates over `Collection.forEach` suffer from extreme N+1 query overhead and hold the DB lock for too long.
**Action:** Outside the transaction, map entities to bulk lists and deduplicate them by ID to prevent SQLite constraint exceptions, then call dedicated bulk `@Upsert`/`@Insert` queries inside the transaction.
