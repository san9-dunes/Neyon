## 2024-05-28 - ExtendedFloatingActionButton Accessibility on Scroll
**Learning:** When using `ExtendedFloatingActionButton` that shrinks to icon-only on scroll, explicitly define `android:contentDescription` and `android:tooltipText`, as the accessibility context provided by `android:text` is lost in the collapsed state.
**Action:** Always add `contentDescription` and `tooltipText` to `ExtendedFloatingActionButton`s configured with `ShrinkOnScrollBehavior` or equivalent.
