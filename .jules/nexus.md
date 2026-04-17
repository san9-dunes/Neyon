## 2026-04-16 - OkHttpClient Connection Pool Reuse
**Pattern:** Individual repositories and authenticators instantiating new `OkHttpClient` objects instead of reusing `baseHttpClient`.
**Learning:** Doing so leads to connection pool fragmentation and higher memory usage.
**Rule:** Always use `baseHttpClient.newBuilder()` when a customized `OkHttpClient` is needed.
