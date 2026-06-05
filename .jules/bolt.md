## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - Bulk Insert Optimization in SuggestionRepository
**Learning:** Found an N+1 insertion problem in `SuggestionRepository.replace` where suggestions, tags, and manga were inserted one by one in a loop (`suggestions.forEach { ... }`) within a transaction. Room can process these much faster as a bulk operation by mapping the collections beforehand and using `@Upsert` with `Collection`.
**Action:** Always verify if iterative DAO `insert`/`update`/`upsert`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance. Ensure that tags and relations are correctly bulked by introducing helper methods in Daos (e.g., `upsertAllWithTags`).
