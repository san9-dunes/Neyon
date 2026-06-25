## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2025-05-24 - Suggestion Repository Bulk Upsert
**Learning:** Room database transactions that perform multiple isolated `upsert` queries in a loop (like in `SuggestionRepository.replace`) cause massive SQLite transaction overhead and poor performance.
**Action:** Always batch entities (like `SuggestionEntity`, `TagEntity`, and `MangaEntity`) into lists and use `upsertAll(Collection<T>)` inside the transaction to execute a single bulk operation per table, dramatically reducing overhead.
