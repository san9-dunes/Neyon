## 2024-05-10 - Explicit Accessibility Context for Collapsing Buttons
**Learning:** `ExtendedFloatingActionButton` elements that use custom behaviors to shrink to an icon-only state on scroll (such as `MainActionButtonBehavior` or `ShrinkOnScrollBehavior`) lose their `android:text` label and its associated accessibility context.
**Action:** Always explicitly define `android:contentDescription` for screen readers and `android:tooltipText` for visual hover/long-press feedback on these specific buttons to ensure the micro-UX is retained in all view states.
