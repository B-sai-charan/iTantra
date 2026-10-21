import re

with open('scratch/p_ui_final.xml', 'r', encoding='utf-8', errors='ignore') as f:
    xml = f.read()

for btn in ['btn_lang_en', 'btn_lang_hi', 'btn_ptt', 'btn_start_capture', 'btn_stop_capture']:
    m = re.search(r'resource-id="org.itantra.app:id/' + btn + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if m:
        cx = (int(m.group(1)) + int(m.group(3))) // 2
        cy = (int(m.group(2)) + int(m.group(4))) // 2
        print(f"{btn:20} -> ({cx}, {cy}) bounds=[{m.group(1)},{m.group(2)}][{m.group(3)},{m.group(4)}]")
    else:
        print(f"{btn:20} -> NOT FOUND")
