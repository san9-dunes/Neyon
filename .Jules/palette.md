
## 2024-05-18 - [Add Missing TooltipText and ContentDescription to Shrinkable FABs]
**Learning:** `ExtendedFloatingActionButton` elements configured to shrink on scroll (using behaviors like `io.github.landwarderer.neyon.core.ui.util.ShrinkOnScrollBehavior`) lose the accessibility context provided by `android:text` when they collapse into icon-only states. Relying solely on `android:text` creates an accessibility void during the collapsed state.
**Action:** Always explicitly define both `android:contentDescription` and `android:tooltipText` for `ExtendedFloatingActionButton` and standard `FloatingActionButton` elements, even if they have `android:text`, to ensure continuous screen reader context and visual hover/long-press feedback across all view states.
