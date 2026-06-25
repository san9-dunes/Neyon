## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Relation Updates in Room
**Learning:** When migrating a sequential `.forEach` loop that processes Room database inserts into a bulk `upsertAll`, remember that simply aggregating the inputs isn't enough. If child relation updates clear existing relations, you must unconditionally capture the parent IDs to clear, rather than conditionally capturing them only when new children exist. Otherwise, empty child lists fail to delete the stale pre-existing children in the database.
**Action:** When performing bulk updates on one-to-many or many-to-many relations, always map out the full `parentIdsToUpdate` list independently of the new relations lists before calling `clearRelations(parentIdsToUpdate)`.
