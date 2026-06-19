## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-25 - Room Database Bulk Upsert Optimization
**Learning:** Found an N+1 query pattern in `SuggestionRepository.replace` where suggestions, tags, and manga were inserted one-by-one in a loop via `upsert`. SQLite requires a full transaction wrapper for each loop iteration unless handled at the DAO level with collections.
**Action:** Always map entities to collections first and utilize Room's `@Upsert`/`@Insert` functions that accept a `Collection<T>`. When dealing with relations (e.g., Manga and Tags), clear the junction table explicitly for the target IDs, then insert the new junction entities in a single batch to drastically reduce SQLite IPC overhead.
