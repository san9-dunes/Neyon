## 2024-05-15 - [ExtendedFloatingActionButton Accessibility]
**Learning:** When using `ExtendedFloatingActionButton` that shrinks to icon-only on scroll, explicitly define `android:contentDescription` and `android:tooltipText`, as the accessibility context provided by `android:text` is lost in the collapsed state.
**Action:** Always provide `android:contentDescription` and `android:tooltipText` matching `android:text` to `ExtendedFloatingActionButton` components that can collapse to icon-only.
