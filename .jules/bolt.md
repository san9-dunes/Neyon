## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - N+1 Insert Optimization in SuggestionRepository
**Learning:** `SuggestionRepository.replace` had a hidden N+1 operation where it iterated over suggestions and called `db.getMangaDao().upsert()` (which inserted each manga and its tags) and `db.getSuggestionDao().upsert()` individually for each item. This led to thousands of individual SQLite transactions for a batch replace operation.
**Action:** When performing bulk replaces or updates that touch multiple DAOs (like Manga, Tags, and Suggestions), extract all entities into collections first, deduplicate by ID, and use bulk `@Upsert` methods (`upsertAll`, `upsertAllWithTags`) inside a single database transaction.
