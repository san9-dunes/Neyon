## 2024-05-24 - Missing tooltipText for ExtendedFloatingActionButtons that shrink on scroll
**Learning:** ExtendedFloatingActionButtons that use `android:text` but shrink on scroll to an icon-only state lose their text context. Without `android:contentDescription` and `android:tooltipText`, they become completely inaccessible to screen readers and mouse users in their collapsed state.
**Action:** Always provide `android:contentDescription` and `android:tooltipText` for `ExtendedFloatingActionButton` if it shrinks.
