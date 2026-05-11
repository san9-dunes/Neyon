## 2024-05-11 - Add tooltip to shrinking ExtendedFloatingActionButtons
**Learning:** When an `ExtendedFloatingActionButton` shrinks on scroll (via `ShrinkOnScrollBehavior`), the context provided by `android:text` is lost. Without `android:contentDescription` and `android:tooltipText`, screen readers and mouse/long-press interactions lack proper context for the resulting icon-only button state.
**Action:** Always ensure `ExtendedFloatingActionButton`s have `contentDescription` and `tooltipText` when their `text` property may be hidden dynamically or via behavior changes.
