## 2026-05-22 - ExtendedFloatingActionButton Accessibility
**Learning:** When using `ExtendedFloatingActionButton` that shrinks to icon-only on scroll, explicitly define `android:contentDescription` and `android:tooltipText`, as the accessibility context provided by `android:text` is lost in the collapsed state.
**Action:** Always provide `tooltipText` and `contentDescription` to FAB elements, especially those utilizing the `ShrinkOnScrollBehavior` or custom behaviors that collapse the view, to ensure full accessibility support in all states.
