## 2024-05-24 - Bulk Insert Optimization in Room
**Learning:** Found an N+1 insertion problem in `MangaDao.upsert` where tags were inserted one by one in a loop (`.forEach { insert(it) }`). Room can process these much faster as a single bulk operation (`insert(tags)`).
**Action:** Always verify if iterative DAO `insert`/`update`/`delete` calls can be replaced with a single method accepting a `Collection` for better performance.
## 2024-05-24 - OkHttpClient Creation in WebView Interception
**Learning:** Found a memory leak and connection pool fragmentation issue where `OkHttpClient.Builder().build()` was called on *every* intercepted `WebResourceRequest` inside `WebViewClient.shouldInterceptRequest` (e.g. `CloudFlareInterceptClient` and `CaptchaContinuationClient`).
**Action:** Always inject a base `OkHttpClient` and use `baseHttpClient.newBuilder().build()` to create a single reusable instance (e.g., via `by lazy`) to preserve the shared connection pool, thread pools, and avoid memory exhaustion when handling hundreds of rapid webview resource requests.
