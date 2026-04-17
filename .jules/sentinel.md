## 2025-02-27 - [Sentinel] Prevent local file exfiltration via WebView in parsers
**Vulnerability:** WebView configured with `javaScriptEnabled = true` but missing explicit `allowFileAccess = false` check. This configuration is used for Manga parsers and leaves the app vulnerable to local file read via `file://` scheme because `allowFileAccess` is true by default on older API levels (the app supports `minSdk = 23`).
**Learning:** Even internal helper methods like `WebView.configureForParser` should strictly enforce `allowFileAccess = false` unless local file access is strictly required, particularly when JavaScript execution is explicitly permitted on untrusted content.
**Prevention:** Always verify that `allowFileAccess = false` is explicitly set when initializing `WebView`s with JavaScript enabled to mitigate file-based XSS attacks on older Android versions.

## 2025-02-27 - [Sentinel] Enable Safe Browsing for WebViews
**Vulnerability:** The application had `android.webkit.WebView.EnableSafeBrowsing` set to `false` in `AndroidManifest.xml`. This disables Android's built-in Safe Browsing feature for WebViews, allowing the application to silently load potentially malicious URLs (phishing, malware) without warning the user.
**Learning:** Safe Browsing is a crucial layer of defense, especially in applications that load untrusted or third-party content (like manga parsers). It should always be explicitly enabled in the manifest unless there is a critical, highly specific reason not to.
**Prevention:** Ensure the `android.webkit.WebView.EnableSafeBrowsing` metadata tag is set to `true` in `AndroidManifest.xml` and verify it during security reviews.
