import xml.etree.ElementTree as ET
import os

ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
ET.register_namespace('app', 'http://schemas.android.com/apk/res-auto')
ET.register_namespace('tools', 'http://schemas.android.com/tools')

files_to_fix = [
    ('app/src/main/res/layout/activity_categories.xml', '@string/add_new_category'),
    ('app/src/main/res/layout/activity_manga_directories.xml', '@string/pick_custom_directory'),
    ('app/src/main/res/layout/activity_main.xml', '@string/_continue'),
    ('app/src/main/res/layout/activity_history_migration.xml', '@string/migrate_all_unavailable'),
    ('app/src/main/res/layout/navigation_rail_fab.xml', '@string/_continue'),
]

for file, tooltip in files_to_fix:
    with open(file, 'r') as f:
        content = f.read()

    # We'll just use string replacement for safety, to preserve formatting
    if file == 'app/src/main/res/layout/activity_categories.xml':
        content = content.replace('android:contentDescription="@string/add_new_category"', 'android:contentDescription="@string/add_new_category"\n\t\tandroid:tooltipText="@string/add_new_category"')
    elif file == 'app/src/main/res/layout/activity_manga_directories.xml':
        content = content.replace('android:contentDescription="@string/pick_custom_directory"', 'android:contentDescription="@string/pick_custom_directory"\n\t\tandroid:tooltipText="@string/pick_custom_directory"')
    elif file == 'app/src/main/res/layout/activity_main.xml':
        content = content.replace('android:text="@string/_continue"', 'android:text="@string/_continue"\n\t\tandroid:contentDescription="@string/_continue"\n\t\tandroid:tooltipText="@string/_continue"')
    elif file == 'app/src/main/res/layout/activity_history_migration.xml':
        content = content.replace('android:text="@string/migrate"', 'android:text="@string/migrate"\n\t\tandroid:contentDescription="@string/migrate_all_unavailable"\n\t\tandroid:tooltipText="@string/migrate_all_unavailable"')
    elif file == 'app/src/main/res/layout/navigation_rail_fab.xml':
        content = content.replace('android:text="@string/_continue"', 'android:text="@string/_continue"\n\t\tandroid:contentDescription="@string/_continue"\n\t\tandroid:tooltipText="@string/_continue"')

    with open(file, 'w') as f:
        f.write(content)
