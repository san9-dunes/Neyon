## 2024-05-18 - ExtendedFloatingActionButton Accessibility
**Learning:** `ExtendedFloatingActionButton`s with `android:text` lose their accessibility context when they shrink to an icon-only state upon scrolling.
**Action:** Always define `android:contentDescription` and `android:tooltipText` explicitly on `ExtendedFloatingActionButton` to ensure screen readers and visual hover/long-press feedback continue to work in the collapsed state.
