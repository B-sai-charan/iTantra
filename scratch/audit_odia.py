import base64

def audit_odia():
    tokens_file = 'app/src/main/assets/models/whisper-tiny/tiny-tokens.txt'
    with open(tokens_file, 'r', encoding='utf-8') as f:
        lines = [l.strip() for l in f if l.strip()]

    odia_lang_tags = []
    odia_script_tokens = []

    for idx, line in enumerate(lines):
        parts = line.split()
        if len(parts) >= 2:
            try:
                dec = base64.b64decode(parts[0]).decode('utf-8', errors='ignore')
                if any(tag in dec for tag in ['<|or|>', '<|ory|>', '<|odia|>', '<|od|>']):
                    odia_lang_tags.append((parts[1], dec))
                if any('\u0B00' <= c <= '\u0B7F' for c in dec):
                    odia_script_tokens.append((parts[1], dec))
            except Exception:
                pass

    print('Odia language tags found:', odia_lang_tags)
    print(f'Odia script tokens (\\u0B00..\\u0B7F) count: {len(odia_script_tokens)}')
    if odia_script_tokens:
        print('Sample Odia script tokens:', odia_script_tokens[:5])

    # Check encoder metadata
    with open('app/src/main/assets/models/whisper-tiny/tiny-encoder.int8.onnx', 'rb') as f:
        enc_data = f.read()

    pos = enc_data.find(b'all_language_codes')
    if pos != -1:
        codes_str = enc_data[pos:pos+400]
        has_or = b',or,' in codes_str or b'or,' in codes_str
        print('Has "or" language code in encoder metadata:', has_or)
        print('Encoder language codes excerpt:', codes_str[:250].decode('ascii', errors='ignore'))

if __name__ == '__main__':
    audit_odia()
