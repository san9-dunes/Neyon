import os
import xml.etree.ElementTree as ET

def check_file(filepath):
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        issues = []
        # Find all ImageButton and FloatingActionButton elements
        for elem in root.iter():
            if 'ImageButton' in elem.tag or 'FloatingActionButton' in elem.tag:
                content_desc = elem.attrib.get('{http://schemas.android.com/apk/res/android}contentDescription')
                tooltip = elem.attrib.get('{http://schemas.android.com/apk/res/android}tooltipText')
                if not content_desc or not tooltip:
                    issues.append(f"<{elem.tag} id={elem.attrib.get('{http://schemas.android.com/apk/res/android}id', 'unknown')}>: contentDescription={content_desc}, tooltipText={tooltip}")
        if issues:
            print(f"File: {filepath}")
            for issue in issues:
                print(f"  {issue}")
    except Exception as e:
        # Ignore parsing errors for now or print them
        pass

for root_dir, dirs, files in os.walk('app/src/main/res/layout'):
    for file in files:
        if file.endswith('.xml'):
            check_file(os.path.join(root_dir, file))
