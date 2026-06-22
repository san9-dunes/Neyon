## 2025-02-27 - [Sentinel] Prevent local file exfiltration via WebView in parsers
**Vulnerability:** WebView configured with `javaScriptEnabled = true` but missing explicit `allowFileAccess = false` check. This configuration is used for Manga parsers and leaves the app vulnerable to local file read via `file://` scheme because `allowFileAccess` is true by default on older API levels (the app supports `minSdk = 23`).
**Learning:** Even internal helper methods like `WebView.configureForParser` should strictly enforce `allowFileAccess = false` unless local file access is strictly required, particularly when JavaScript execution is explicitly permitted on untrusted content.
**Prevention:** Always verify that `allowFileAccess = false` is explicitly set when initializing `WebView`s with JavaScript enabled to mitigate file-based XSS attacks on older Android versions.

## 2025-02-27 - [Sentinel] Enable Safe Browsing for WebViews
**Vulnerability:** The application had `android.webkit.WebView.EnableSafeBrowsing` set to `false` in `AndroidManifest.xml`. This disables Android's built-in Safe Browsing feature for WebViews, allowing the application to silently load potentially malicious URLs (phishing, malware) without warning the user.
**Learning:** Safe Browsing is a crucial layer of defense, especially in applications that load untrusted or third-party content (like manga parsers). It should always be explicitly enabled in the manifest unless there is a critical, highly specific reason not to.
**Prevention:** Ensure the `android.webkit.WebView.EnableSafeBrowsing` metadata tag is set to `true` in `AndroidManifest.xml` and verify it during security reviews.

## 2025-03-01 - [Sentinel] Secure Network Security Configuration
**Vulnerability:** The application had `cleartextTrafficPermitted="true"` in its `network_security_config.xml` `<base-config>`. Additionally, it accepted user-installed CAs (`<certificates src="user" />`) in production. This leaves the app vulnerable to Man-in-the-Middle (MitM) attacks by allowing unencrypted HTTP connections globally and permitting malicious user-installed certificates to intercept HTTPS traffic.
**Learning:** `network_security_config.xml` `<base-config>` must always prioritize security. Disabling cleartext traffic ensures that unencrypted connections are structurally rejected by the Android network stack unless explicitly authorized. User-provided certificates must never be trusted in production environments to avoid MitM.
**Prevention:**
1) Set `cleartextTrafficPermitted="false"` in `<base-config>`.
2) Restrict `<base-config>` trust anchors strictly to `<certificates src="system" />`.
3) Confine `<certificates src="user" />` explicitly to `<debug-overrides>` so they only operate during local development.
4) If specific domains genuinely require cleartext connections (e.g. `neverssl.com`), whitelist them selectively via `<domain-config cleartextTrafficPermitted="true">`.

## 2025-03-05 - [Sentinel] Secure WebView File Access
**Vulnerability:** The application configured `WebView`s used for Manga parsing with `javaScriptEnabled = true` and `allowFileAccess = false`, but omitted `allowFileAccessFromFileURLs = false` and `allowUniversalAccessFromFileURLs = false`. Because the app supports `minSdk = 23`, these settings default to `true` on older APIs, potentially allowing malicious local files or scripts to exfiltrate other local files or cross-origin data.
**Learning:** Even if `allowFileAccess` is `false`, on older Android API levels (pre-API 30), `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` must be explicitly set to `false` when `javaScriptEnabled = true` to fully mitigate local file exfiltration risks. The `@Suppress("DEPRECATION")` annotation is required as these methods are deprecated in newer SDKs where they default to false.
**Prevention:** Always verify that all file access vectors (`allowFileAccess`, `allowFileAccessFromFileURLs`, `allowUniversalAccessFromFileURLs`) are explicitly disabled in custom `WebView` configurations when JavaScript execution is permitted, regardless of deprecation warnings on newer APIs.
