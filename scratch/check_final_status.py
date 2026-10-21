import subprocess, os, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'
dev_b = 'HNP097HQ'

for d, name in [(dev_a, 'DEV_A'), (dev_b, 'DEV_B')]:
    subprocess.run([adb, '-s', d, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
    xml = subprocess.run([adb, '-s', d, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
    root = ET.fromstring(xml)
    print(f'=== UI STATE FOR {name} ===')
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        t = n.attrib.get('text', '')
        if any(k in rid for k in ['tv_connection_state', 'tv_you_transcript', 'tv_remote_transcript', 'tv_status', 'tv_ptt_label', 'btn_lang_en', 'btn_lang_hi']):
            print(f'{rid} -> "{t}"')
