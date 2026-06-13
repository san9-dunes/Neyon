
## 2024-05-24 - Tooltips on Uninstall Actions
**Learning:** The item_repo layout lacked visual hover feedback on its destructive icon-only delete button, though it had screen-reader support.
**Action:** Always pair `android:contentDescription` with `android:tooltipText` on icon-only `ImageButton`s to support both visual and screen-reader accessibility.
