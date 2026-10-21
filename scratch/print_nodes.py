import xml.etree.ElementTree as ET

tree = ET.parse('scratch/p_ui_before.xml')
for node in tree.iter('node'):
    rid = node.attrib.get('resource-id', '')
    txt = node.attrib.get('text', '')
    bounds = node.attrib.get('bounds', '')
    if rid or txt:
        try:
            print(f"{rid:40} | {txt[:30]:30} | {bounds}")
        except Exception:
            print(f"{rid:40} | [non-ascii text] | {bounds}")
