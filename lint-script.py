import xml.etree.ElementTree as ET
import glob

files = glob.glob('app/src/main/res/values/*.xml')
for f in files:
    try:
        ET.parse(f)
    except ET.ParseError as e:
        print(f"XML Parsing error in {f}: {e}")
