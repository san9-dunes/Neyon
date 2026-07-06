## 2024-07-06 - Tooltips for icon-only buttons in reader bottom bar
**Learning:** Icon-only buttons with `contentDescription` provide accessibility to screen readers, but sighted users (e.g., using a mouse, or pressing and holding on touch) also need visual hints to understand the icons.
**Action:** Always replicate `contentDescription` into `tooltipText` (or use a distinct string if better suited) for icon-only buttons to ensure they have visual tooltips. Use native `android:tooltipText` rather than `app:tooltipText` for consistency, as Android handles it fine on API < 26.
