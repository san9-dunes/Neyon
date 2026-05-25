## 2024-05-19 - Shrinkable FAB Context Loss
**Learning:** When using `ExtendedFloatingActionButton` with behaviors that shrink it to an icon-only state on scroll (like `MainActionButtonBehavior` or `ShrinkOnScrollBehavior`), the context provided by `android:text` is lost visually and for screen readers.
**Action:** Always explicitly define `android:contentDescription` and `android:tooltipText` on shrinkable `ExtendedFloatingActionButton`s to maintain context in the collapsed state.
