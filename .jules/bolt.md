## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2026-07-06 - Optimization of FavouritesRepository addToCategory
**Learning:** Replaced iterative loops for bulk database inserts with bulk DAO methods (upsertAllWithTags and insertAll) when adding multiple mangas to a category. Avoiding N+1 database queries significantly reduces SQLite transaction overhead and execution time when performing operations over large collections.
**Action:** Always use bulk collection operations in Room DAOs for loops that perform database operations over large lists.
