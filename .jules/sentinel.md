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

## 2024-05-24 - Missing Explicit File Access Denials in WebView Config
**Vulnerability:** WebView configured with `javaScriptEnabled = true` and `allowFileAccess = false`, but missing explicit assignments for `allowFileAccessFromFileURLs = false` and `allowUniversalAccessFromFileURLs = false`. On older API levels (app supports `minSdk = 23`), these default to true, leaving the app vulnerable to local file exfiltration vulnerabilities via the `file://` scheme.
**Learning:** Always explicitly disable all file access related flags in WebViews configured for parsing or executing arbitrary JS, regardless of `minSdk`.
**Prevention:** Set `allowFileAccess = false`, `allowFileAccessFromFileURLs = false`, and `allowUniversalAccessFromFileURLs = false` on WebView settings whenever `javaScriptEnabled = true`.
