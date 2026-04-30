## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.

## 2024-06-25 - Prevent OkHttpClient fragmentation in WebViews
**Learning:** Found massive memory and CPU overhead caused by `OkHttpClient.Builder().build()` being called on every intercepted request inside `shouldInterceptRequest` of `CloudFlareInterceptClient` and `CaptchaContinuationClient`. Because these run on background threads, they spawn a new connection pool and thread pool per request.
**Action:** Always inject a global `@BaseHttpClient` and lazily instantiate custom variations using `.newBuilder()` rather than calling `Builder().build()` from scratch in high-frequency interception points.
