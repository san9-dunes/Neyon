
## 2024-05-29 - ExtendedFloatingActionButton Accessibility
**Learning:** ExtendedFloatingActionButtons that shrink to an icon-only state on scroll lose the accessibility context provided by `android:text`.
**Action:** Explicitly define `android:contentDescription` and `android:tooltipText` to ensure accessibility is maintained in the collapsed state.
