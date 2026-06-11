## 2024-06-11 - Add tooltips to icon-only buttons
**Learning:** Found an accessibility issue pattern specific to this app's components: icon-only interactive elements (`ImageButton`, `FloatingActionButton`) have `contentDescription` for screen readers but lack `tooltipText` for visual hover/long-press feedback.
**Action:** Always add `android:tooltipText` to icon-only buttons to improve micro-UX for users relying on visual tooltips.
