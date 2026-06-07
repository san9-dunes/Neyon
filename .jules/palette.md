## 2024-06-07 - Add tooltips to FABs
**Learning:** In Android XML layouts, even interactive elements with visible labels (like ExtendedFloatingActionButton) or `contentDescription` need `android:tooltipText` for visual hover feedback on Android 8.0+ devices.
**Action:** Always verify that both `contentDescription` (for screen readers) and `tooltipText` (for visual hover) are present on FABs and ImageButtons. Never duplicate strings into `contentDescription` if the button text suffices, but always provide `tooltipText`.
