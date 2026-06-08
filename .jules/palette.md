
## 2024-05-18 - Tooltips for icon-only actions
**Learning:** In Android XML layouts, adding `android:tooltipText` to icon-only interactive elements (like `FloatingActionButton` or `ImageButton`) is a crucial micro-UX enhancement that complements `android:contentDescription`. It provides visual hover/long-press feedback, improving discoverability for non-screen-reader users.
**Action:** Always ensure icon-only buttons define both `android:contentDescription` (for screen readers) and `android:tooltipText` (for visual users) using the same or appropriately descriptive string resources.
