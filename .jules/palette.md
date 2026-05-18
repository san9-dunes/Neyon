## 2024-05-18 - ExtendedFloatingActionButton Accessibility
**Learning:** `ExtendedFloatingActionButton` elements which shrink on scroll require explicitly defining both `android:contentDescription` and `android:tooltipText`, because their visual label (`android:text`) context is lost in the collapsed state.
**Action:** When adding `ExtendedFloatingActionButton` that shrinks to icon-only on scroll, ensure both `contentDescription` and `tooltipText` are provided for accessibility and hover states.
