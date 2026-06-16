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

## 2025-03-01 - [Sentinel] Prevent local file exfiltration via WebView file URLs in parsers
**Vulnerability:** While `allowFileAccess = false` was set for parsers' WebViews, `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` were not explicitly disabled. Given `javaScriptEnabled = true`, this creates a vulnerability on older API levels (the app supports `minSdk = 23`) where an attacker could execute JavaScript to read local files via the `file://` scheme.
**Learning:** Even when `allowFileAccess` is disabled, the deprecated settings `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` must also be explicitly set to `false` when JavaScript is enabled, to comprehensively block file-based XSS and exfiltration on older Android versions.
**Prevention:** Always ensure `allowFileAccessFromFileURLs = false` and `allowUniversalAccessFromFileURLs = false` are explicitly set (using `@Suppress("DEPRECATION")` for compatibility) when configuring WebViews with JavaScript enabled, to mitigate local file read vulnerabilities on earlier Android APIs.
