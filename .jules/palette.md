## $(date +%Y-%m-%d) - Add tooltipText to delete repository icon
**Learning:** In Android RecyclerView items, icon-only interactive elements like ImageButton often define `android:contentDescription` for screen readers but miss `android:tooltipText` for visual hover/long-press feedback. This is a common accessibility/micro-UX issue in the project.
**Action:** When working on Android XML layouts for this project, always ensure icon-only interactive elements (e.g., ImageButton) define both `contentDescription` and `tooltipText`.
