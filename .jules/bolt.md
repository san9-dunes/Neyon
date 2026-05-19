## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## $(date +%Y-%m-%d) - Optimization of Suggestion Insertions
**Learning:** Found an N+1 query problem in `SuggestionRepository.replace` where suggestions were inserted iteratively inside a `forEach` block over a database transaction. Each iteration triggered multiple DAO calls.
**Action:** Always batch related entities outside of loops and perform bulk DAO operations (like `upsertAll`) to minimize SQLite transaction overhead.
