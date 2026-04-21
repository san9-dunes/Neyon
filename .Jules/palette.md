## 2024-05-24 - Missing tooltipText on interactive elements
**Learning:** Found that `FloatingActionButton` and other interactive elements like `ImageButton` often lack the `tooltipText` attribute while having `contentDescription`. The `tooltipText` is crucial for desktop/mouse users and for long-press tooltips on Android.
**Action:** Always verify if `tooltipText` is present alongside `contentDescription` for icon-only interactive elements in Android XML layouts.
