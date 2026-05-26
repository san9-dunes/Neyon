## 2026-05-26 - Extended FAB Shrink Behavior Accessibility
**Learning:** ExtendedFloatingActionButtons that use `ShrinkOnScrollBehavior` (which collapses the button to an icon-only state) lose the accessibility context provided by `android:text`.
**Action:** Always explicitly define `android:contentDescription` and `android:tooltipText` on ExtendedFloatingActionButtons to ensure screen readers and visual hover states still function correctly when collapsed.
