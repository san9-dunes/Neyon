## 2024-05-30 - Add tooltip and content description for shrinkable ExtendedFloatingActionButtons
**Learning:** The accessibility context provided by `android:text` is lost when an `ExtendedFloatingActionButton` shrinks to an icon-only state.
**Action:** Always define `android:contentDescription` and `android:tooltipText` matching the label or function of the button to preserve context for screen readers and pointer-hover when collapsed.
