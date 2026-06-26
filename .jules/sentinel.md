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

## 2025-03-05 - [Sentinel] Fix WebView file exfiltration vulnerability
**Vulnerability:** The `WebView.configureForParser` method configured WebViews with `javaScriptEnabled = true` and `allowFileAccess = false`, but did not explicitly disable `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs`. Since the application supports API level 23 (`minSdk = 23`), failing to set these explicitly to `false` leaves older Android versions vulnerable to cross-origin data leaks and local file exfiltration if malicious JavaScript executes in a file scheme context.
**Learning:** For backward compatibility on older Android versions, it is not enough to just set `allowFileAccess = false`. The properties `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` must also be strictly configured. Since these properties are deprecated on modern APIs (API 30+), they require the `@Suppress("DEPRECATION")` annotation to silence compiler warnings while ensuring the security configuration is universally applied.
**Prevention:** Whenever configuring Android WebViews that enable JavaScript and support `minSdk < 30`, explicitly disable `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` in addition to `allowFileAccess`, and manage the compiler warning with `@Suppress("DEPRECATION")`.
