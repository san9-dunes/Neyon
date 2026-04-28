## 2026-04-28 - Missing tooltipText on icon-only buttons
**Learning:** Found multiple instances where `ImageButton` or `FloatingActionButton` had a `contentDescription` for screen readers but lacked a `tooltipText` for visual hover/long-press feedback, leading to a degraded micro-UX experience for users relying on those interactions.
**Action:** Always add `android:tooltipText` matching the `android:contentDescription` on icon-only interactive elements in Android XML layouts to ensure consistent accessibility and discoverability.
