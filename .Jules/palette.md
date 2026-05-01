## 2024-05-18 - ExtendedFloatingActionButton Accessibility
**Learning:** When using `ExtendedFloatingActionButton` that often shrink to icon-only buttons on scrolling behavior, explicitly defining `android:contentDescription` and `android:tooltipText` is required because the accessibility context provided by `android:text` may be lost during the collapsed state.
**Action:** Always provide explicit tooltips and content descriptions on all interactive Image and FAB views, regardless of if they also contain text initially.
