
## 2024-05-24 - Icon-only buttons accessibility
**Learning:** Many icon-only buttons (like `ImageButton` and `FloatingActionButton`) in the app's XML layouts define `contentDescription` for screen readers but omit `tooltipText`, depriving visual users of hover or long-press contextual feedback.
**Action:** Always ensure both `android:contentDescription` and `android:tooltipText` are defined for icon-only interactive elements to provide complete accessibility coverage for all users.
