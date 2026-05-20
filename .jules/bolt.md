## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-05-27 - Bulk Insert Optimization in Suggestions
**Learning:** Found an N+1 insertion problem in `SuggestionRepository.replace` where suggestions and tags were inserted one by one in a loop (`suggestions.forEach { upsert(it) }`). Room processes bulk operations significantly faster.
**Action:** Always refactor iterative DAO `upsert` calls by grouping entities into collections before transactions and using bulk methods (like `upsertAll(entities: Collection<T>)`) to resolve N+1 query overhead.
