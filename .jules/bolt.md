## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Upsert Optimization in SuggestionRepository
**Learning:** Found an N+1 insertion problem in `SuggestionRepository.replace` where suggestions, tags, and tag-relations were inserted one by one in a loop (`.forEach { ... db.getMangaDao().upsert(manga, tags) }`). Mapping them into collections first and executing a bulk `upsertAll` is significantly faster due to reduced SQLite transaction overhead.
**Action:** Extract entity mapping logic outside of iterative loops and utilize bulk DAO methods (`upsertAll`, `insertAll`, etc.) for database operations when processing lists.
