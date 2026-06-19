## 2024-10-24 - Missing Tooltips on Interactive Elements
**Learning:** Found several `FloatingActionButton`, `ExtendedFloatingActionButton` and `ImageButton` elements missing `android:tooltipText` attributes even though they had `android:contentDescription`. Tooltips provide critical visual feedback for users, especially on icon-only or primary action buttons.
**Action:** Always check `android:tooltipText` along with `android:contentDescription` for floating action buttons and image buttons. Use the same string resource if it accurately describes the action.
