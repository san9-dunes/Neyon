## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-25 - Bulk Insert Optimization in Bookmarks
**Learning:** Found an N+1 insertion problem in `BookmarksRepository.removeBookmarks`'s `reverse()` method where bookmarks were inserted one by one in a loop (`for (e in entities) { db.getBookmarksDao().insert(e) }`). Room can process these much faster as a single bulk operation (`insert(entities)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
