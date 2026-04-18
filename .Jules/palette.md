## 2024-03-24 - [Add tooltips to ImageButtons]
**Learning:** Found several standard icon-only buttons (`expand`, `close`, `open_in_browser`) missing `tooltipText` across the application's XML layouts (`item_download.xml`, `fragment_preview.xml`, `sheet_scrobbling.xml`, `navigation_rail_fab.xml`, `view_scroll_timer.xml`).
**Action:** Always add `tooltipText` to icon-only buttons (`ImageButton`) that use `contentDescription` to improve accessibility for sighted users relying on mouse hover or long-press.
