import socket
import struct
import zlib
import time
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

def encode_packet(text: str, language_id: int, seq: int = 1, timestamp_ms: int = None, extended_tag: str = None) -> bytes:
    if timestamp_ms is None:
        timestamp_ms = int(time.time() * 1000)
    
    text_bytes = text.encode('utf-8')
    text_len = len(text_bytes)
    
    # Pre-Header: Magic (2B: 0x4954), Version (1B: 0x01), MessageType (1B: 0x01), Flags (1B: 0x00), Seq (4B: UInt32), Ts (8B: Int64)
    magic = 0x4954
    version = 0x01
    msg_type = 0x01
    flags = 0x00
    
    pre_header = struct.pack(">HBBBIq", magic, version, msg_type, flags, seq, timestamp_ms)
    
    # Language
    if language_id == 0xFE: # EXTENDED_BCP47
        ext_bytes = extended_tag.encode('utf-8')
        lang_bytes = struct.pack(">BB", language_id, len(ext_bytes)) + ext_bytes
    else:
        lang_bytes = struct.pack(">B", language_id)
        
    # Text length (UInt16)
    text_len_bytes = struct.pack(">H", text_len)
    
    payload_so_far = pre_header + lang_bytes + text_len_bytes + text_bytes
    
    # CRC-32 (IEEE 802.3 over Magic through TextPayload)
    crc = zlib.crc32(payload_so_far) & 0xFFFFFFFF
    crc_bytes = struct.pack(">I", crc)
    
    return payload_so_far + crc_bytes

def send_one(host: str, port: int, label: str, text: str, lang_id: int, seq: int):
    print(f"\n[{label}] Connecting to {host}:{port}...")
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5.0)
    s.connect((host, port))
    pkt = encode_packet(text=text, language_id=lang_id, seq=seq)
    s.sendall(pkt)
    print(f"[{label}] Packet sent ({len(pkt)} bytes).")
    time.sleep(1)
    s.close()
    print(f"[{label}] Waiting 12s for synthesis and audio playback...")
    time.sleep(12)

def main():
    host = "172.20.71.62"
    port = 8888
    
    tests = [
        ("TEST 1 (Hindi)", "नमस्ते, यह iTantra का नियंत्रित Zwei-Geräte परीक्षण संदेश है।", 0x02),
        ("TEST 2 (Hindi)", "यह iTantra का दूसरा नियंत्रित हिंदी संदेश है।", 0x02),
        ("TEST 3 (English)", "Hello, this is a controlled iTantra communication test.", 0x01)
    ]
    
    for idx, (label, text, lang_id) in enumerate(tests, 1):
        send_one(host, port, label, text, lang_id, seq=idx)
        
    print("\nAll 3 test packets successfully transmitted!")

if __name__ == "__main__":
    main()
