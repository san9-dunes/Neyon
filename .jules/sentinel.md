## 2026-05-12 - [Local File Exfiltration via WebView]
**Vulnerability:** Android WebViews could potentially allow local file access or universal access from file URLs.
**Learning:** Explicitly setting `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` to `false` prevents malicious scripts loaded via file schemes from exfiltrating local device files, serving as a defense-in-depth measure, especially for older Android versions where defaults may vary.
**Prevention:** Always ensure `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` are explicitly disabled when configuring WebViews with JavaScript enabled, unless absolutely required for specific functionality.
