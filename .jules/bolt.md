## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Insert Optimization in SuggestionRepository
**Learning:** Found an N+1 insertion problem in `SuggestionRepository.replace` where suggestions, tags, and manga relations were upserted one by one in a loop (`suggestions.forEach { ... upsert() ... }`). Moving `db.getTagsDao().upsert` and `db.getSuggestionDao().upsertAll` outside of the loop to process these as a single bulk operation resolves the N+1 query problem, drastically reducing SQLite transaction overhead. It's important to deduplicate `allTags` via `distinctBy { it.id }` prior to upserting.
**Action:** Always verify if iterative DAO `upsert` calls can be replaced with a single method accepting a `Collection` for better performance.
