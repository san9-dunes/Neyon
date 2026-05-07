## 2025-02-08 - Accessibility of Collapsible ExtendedFloatingActionButtons
**Learning:** ExtendedFloatingActionButtons that use `ShrinkOnScrollBehavior` lose their accessibility context (provided by `android:text`) when they collapse into their icon-only state. Without an explicit content description, the button becomes unreadable to screen readers when collapsed.
**Action:** Always explicitly define `android:contentDescription` and `android:tooltipText` on `ExtendedFloatingActionButton`s that shrink, ensuring the icon-only state remains fully accessible and provides visual hover context.
