import subprocess, os, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'

subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root = ET.fromstring(xml)

for n in root.iter('node'):
    rid = n.attrib.get('resource-id', '')
    if any(k in rid for k in ['btn_mode', 'et_peer', 'btn_connect', 'tv_connection', 'tv_status']):
        print(f'{rid} | text: "{n.attrib.get("text")}" | bounds: {n.attrib.get("bounds")}')
