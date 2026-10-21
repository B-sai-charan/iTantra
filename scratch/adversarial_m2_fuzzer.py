"""
Adversarial M2 Protocol & Network Fuzzer for iTantra PS26173
Target: Physical Android Receiver over Hotspot (10.179.231.166:42173)
"""
import socket
import struct
import zlib
import time
import sys

TARGET_HOST = "127.0.0.1"
TARGET_PORT = 42173

MAGIC = 0x4954       # 'IT'
VERSION = 0x01
MSG_SPEECH_TEXT = 0x01

# Language IDs
LANG_UNDEFINED = 0x00
LANG_ENGLISH = 0x01
LANG_HINDI = 0x02
LANG_TELUGU = 0x03
LANG_TAMIL = 0x04
LANG_MARATHI = 0x05
LANG_BENGALI = 0x06
LANG_GUJARATI = 0x07
LANG_KANNADA = 0x08
LANG_MALAYALAM = 0x09
LANG_PUNJABI = 0x0A
LANG_URDU = 0x0B
LANG_ODIA = 0x0C
LANG_ASSAMESE = 0x0D
LANG_EXTENDED = 0xFE

def build_packet(
    magic=MAGIC,
    version=VERSION,
    msg_type=MSG_SPEECH_TEXT,
    flags=0,
    seq=1,
    timestamp=None,
    lang_id=LANG_ENGLISH,
    ext_tag=None,
    text="Hello tactical radio",
    confidence=None,
    corrupt_crc=False,
    trailing_bytes=b""
):
    if timestamp is None:
        timestamp = int(time.time() * 1000)
        
    text_bytes = text.encode("utf-8") if isinstance(text, str) else text
    
    # Pre-header (17 bytes: Magic=2, Version=1, MsgType=1, Flags=1, Seq=4 UInt32, Timestamp=8 Int64)
    pre_header = struct.pack(">HBBBIq", magic, version, msg_type, flags, seq, timestamp)
    
    # Language
    if lang_id == LANG_EXTENDED:
        ext_bytes = ext_tag.encode("utf-8") if isinstance(ext_tag, str) else ext_tag
        lang_section = struct.pack(">BB", lang_id, len(ext_bytes)) + ext_bytes
    else:
        lang_section = struct.pack(">B", lang_id)
        
    # Text length (2 bytes UInt16)
    text_len_section = struct.pack(">H", len(text_bytes) & 0xFFFF)
    
    # Confidence
    conf_section = b""
    if (flags & 0x01) != 0 and confidence is not None:
        conf_section = struct.pack(">B", confidence)
        
    packet_body = pre_header + lang_section + text_len_section + conf_section + text_bytes
    
    # CRC-32
    crc = zlib.crc32(packet_body) & 0xFFFFFFFF
    if corrupt_crc:
        crc ^= 0xDEADBEEF
        
    crc_section = struct.pack(">I", crc)
    
    return packet_body + crc_section + trailing_bytes

def frame_packet(packet_bytes):
    # 4-byte length prefix
    length_header = struct.pack(">I", len(packet_bytes))
    return length_header + packet_bytes

def run_fuzz_tests():
    results = []
    
    test_cases = [
        # (Name, PacketGenerator, ExpectedOutcome)
        ("TC-M2-01: Standard English Speech", 
         lambda: frame_packet(build_packet(text="Alpha One, radio check", lang_id=LANG_ENGLISH)), 
         "ACCEPT_AND_PLAY"),
        
        ("TC-M2-02: Devanagari Hindi Speech", 
         lambda: frame_packet(build_packet(text="नमस्ते, यह सामरिक रेडियो परीक्षण है", lang_id=LANG_HINDI)), 
         "ACCEPT_AND_PLAY"),
         
        ("TC-M2-03: Single Character Text", 
         lambda: frame_packet(build_packet(text="A", lang_id=LANG_ENGLISH)), 
         "ACCEPT_AND_PLAY"),
         
        ("TC-M2-04: Emoji & Unicode Symbols", 
         lambda: frame_packet(build_packet(text="Mission Success 🚀 Target Secure 🎯", lang_id=LANG_ENGLISH)), 
         "ACCEPT_AND_PLAY"),
         
        ("TC-M2-05: Mixed English-Hindi Code-Switch", 
         lambda: frame_packet(build_packet(text="Base station, रिपोर्टिंग स्थिति normal", lang_id=LANG_HINDI)), 
         "ACCEPT_AND_PLAY"),
         
        ("TC-M2-06: Special Punctuation & Quotes", 
         lambda: frame_packet(build_packet(text="Alert: \"Sector 4\" / (Grid 9-Alpha) [URGENT]!", lang_id=LANG_ENGLISH)), 
         "ACCEPT_AND_PLAY"),
         
        ("TC-M2-07: Unsupported Language (Tamil 'ta')", 
         lambda: frame_packet(build_packet(text="வணக்கம் ரேடியோ", lang_id=LANG_TAMIL)), 
         "REJECT_TTS_UNSUPPORTED"),
         
        ("TC-M2-08: Unsupported Language (Telugu 'te')", 
         lambda: frame_packet(build_packet(text="నమస్కారం రేడియో", lang_id=LANG_TELUGU)), 
         "REJECT_TTS_UNSUPPORTED"),
         
        ("TC-M2-09: Unsupported Language (Marathi 'mr')", 
         lambda: frame_packet(build_packet(text="नमस्कार रेडिओ", lang_id=LANG_MARATHI)), 
         "REJECT_TTS_UNSUPPORTED"),
         
        ("TC-M2-10: Extended BCP-47 Tag (en-IN)", 
         lambda: frame_packet(build_packet(text="Check radio from New Delhi", lang_id=LANG_EXTENDED, ext_tag="en-IN")), 
         "ACCEPT_OR_REJECT"),
         
        ("TC-M2-11: Empty Text Payload", 
         lambda: frame_packet(build_packet(text="", lang_id=LANG_ENGLISH)), 
         "HANDLE_EMPTY"),
         
        ("TC-M2-12: Corrupted CRC-32 Checksum", 
         lambda: frame_packet(build_packet(text="Malicious altered payload", corrupt_crc=True)), 
         "DROP_PACKET_CRC_ERROR"),
         
        ("TC-M2-13: Invalid Magic Synchronizer (0xDEAD)", 
         lambda: frame_packet(build_packet(magic=0xDEAD, text="Invalid magic")), 
         "DROP_PACKET_MAGIC_ERROR"),
         
        ("TC-M2-14: Invalid Version (0x99)", 
         lambda: frame_packet(build_packet(version=0x99, text="Invalid version")), 
         "DROP_PACKET_VERSION_ERROR"),
         
        ("TC-M2-15: Invalid MessageType (0x7F)", 
         lambda: frame_packet(build_packet(msg_type=0x7F, text="Invalid type")), 
         "DROP_PACKET_TYPE_ERROR"),
         
        ("TC-M2-16: Malformed UTF-8 in Payload", 
         lambda: frame_packet(build_packet(text=b"\xFF\xFE\xFD\x80\x81", lang_id=LANG_ENGLISH)), 
         "DROP_PACKET_UTF8_ERROR"),
         
        ("TC-M2-17: Trailing Garbage Bytes after CRC", 
         lambda: frame_packet(build_packet(text="Clean text", trailing_bytes=b"\x00\x01\x02\x03\x04")), 
         "DROP_PACKET_TRAILING_ERROR"),
         
        ("TC-M2-18: Zero-Length Transport Frame (Header=0)", 
         lambda: struct.pack(">I", 0), 
         "DISCONNECT_FRAMING_ERROR"),
         
        ("TC-M2-19: Oversized Transport Frame Length (70,000 bytes)", 
         lambda: struct.pack(">I", 70000) + b"\x00" * 100, 
         "DISCONNECT_FRAMING_ERROR"),
         
        ("TC-M2-20: Rapid Burst 5 Consecutive Messages", 
         lambda: b"".join([frame_packet(build_packet(seq=i, text=f"Rapid Burst Sequence #{i}", lang_id=LANG_ENGLISH)) for i in range(1, 6)]), 
         "QUEUE_AND_PLAY_SEQUENTIAL"),
    ]
    
    print(f"=== Starting Adversarial M2 Protocol Testing on {TARGET_HOST}:{TARGET_PORT} ===")
    
    for idx, (name, gen_func, expected) in enumerate(test_cases, 1):
        print(f"\n[{idx}/{len(test_cases)}] Executing: {name}")
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(4.0)
        try:
            sock.connect((TARGET_HOST, TARGET_PORT))
            payload = gen_func()
            sock.sendall(payload)
            print(f"    Sent {len(payload)} bytes. Expected behavior: {expected}")
            time.sleep(1.0)
            results.append((name, "EXECUTED", expected, f"Sent {len(payload)} bytes successfully"))
        except ConnectionRefusedError:
            print(f"    FAILED: Connection Refused on {TARGET_HOST}:{TARGET_PORT}")
            results.append((name, "BLOCKED", expected, "Connection Refused"))
        except Exception as e:
            print(f"    Result / Exception: {e}")
            results.append((name, "EXCEPTION", expected, str(e)))
        finally:
            try:
                sock.close()
            except:
                pass
        time.sleep(0.5)
        
    print("\n=== SUMMARY OF ALL ADVERSARIAL PROTOCOL RUNS ===")
    for name, status, expected, detail in results:
        print(f"{name:55} | {status:10} | {expected:25} | {detail}")

if __name__ == "__main__":
    run_fuzz_tests()
