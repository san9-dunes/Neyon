
## 2024-05-24 - Interactive Elements Missing Tooltips
**Learning:** ExtendedFloatingActionButtons and standalone ImageButtons should specify both contentDescription (for screen readers) and tooltipText (for visual hover support), especially when they can shrink to icon-only states.
**Action:** Always provide `android:tooltipText` alongside `android:contentDescription` or `android:text` for interactive elements that are or can become icon-only.
