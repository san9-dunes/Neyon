## 2024-05-24 - Bulk Operations in Room
**Learning:** Replaced iterative `.forEach { insertTagRelation(it) }` with a single bulk `insertTagRelations(tags)` call. Resolves N+1 query problem, drastically reducing SQLite transaction overhead during bulk upserts.
**Action:** When updating database in loops, use bulk queries (IN clauses or @Upsert with collections) whenever possible. Room >= 2.4.0 automatically chunks IN collections to prevent SQLite parameter limits.

## 2024-05-24 - Bulk Operations in TrackingRepository
**Learning:** Replaced iterative `dao.upsert(TrackEntity.create(mangaId))`, `dao.clearCounter(id)`, and `dao.delete(mangaId)` with single bulk `dao.upsertAll(tracksToInsert)`, `dao.clearCounters(ids)`, and `dao.deleteAll(ids)` calls. Resolves N+1 query problems in TrackingRepository, drastically reducing SQLite transaction overhead during bulk updates. Room >= 2.4.0 automatically chunks IN collections to prevent SQLite parameter limits.
**Action:** When updating database in loops, use bulk queries (IN clauses or @Upsert with collections) whenever possible to prevent performance bottlenecks.
