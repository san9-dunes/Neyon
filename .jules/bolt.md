## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-25 - Room withTransaction N+1 and Lock Contention
**Learning:** Found N+1 query loop for `addToCategory` where each manga and its tags were inserted iteratively within a `db.withTransaction` block. This significantly slows down batch operations and holds the SQLite transaction lock too long, increasing the risk of database contention.
**Action:** Replace iterative inserts inside `withTransaction` blocks with bulk operations (e.g. `@Upsert abstract suspend fun upsertAll(entities: Collection<Entity>)`). Construct mappings and apply `.distinctBy { it.id }` outside the transaction block to minimize database locking time and prevent SQLite constraint errors.
