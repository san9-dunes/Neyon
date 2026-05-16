
## 2024-05-24 - ExtendedFloatingActionButton Accessibility
**Learning:** Interactive elements like `ExtendedFloatingActionButton` that shrink to icon-only on scroll need explicit `contentDescription` and `tooltipText`. The accessibility context provided by `android:text` is lost in the collapsed state, making the button inaccessible to screen readers and missing hover feedback.
**Action:** Always explicitly define `android:contentDescription` and `android:tooltipText` on `ExtendedFloatingActionButton` and `FloatingActionButton` elements, even if they have `android:text`.
