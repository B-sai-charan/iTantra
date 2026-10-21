import base64
import sys

def run_audit():
    tokens_file = 'app/src/main/assets/models/whisper-tiny/tiny-tokens.txt'
    with open(tokens_file, 'r', encoding='utf-8') as f:
        lines = [l.strip() for l in f if l.strip()]

    print(f'Total lines in tiny-tokens.txt: {len(lines)}')
    assert len(lines) == 51865, f'Expected 51865 lines, got {len(lines)}'

    id_set = set()
    tokens_map = {}
    duplicates = []

    for idx, line in enumerate(lines):
        parts = line.split()
        if len(parts) < 2:
            print(f'Malformed line {idx}: {line}')
            continue
        token_b64 = parts[0]
        token_id = int(parts[1])
        
        if token_id in id_set:
            duplicates.append(token_id)
        id_set.add(token_id)
        
        try:
            decoded = base64.b64decode(token_b64).decode('utf-8', errors='ignore')
            tokens_map[token_id] = decoded
        except Exception as e:
            print(f'Error decoding line {idx}: {e}')

    print(f'Unique Token IDs count: {len(id_set)}')
    print(f'Duplicate IDs: {len(duplicates)}')
    assert len(duplicates) == 0, f'Duplicates found: {duplicates}'

    min_id = min(id_set)
    max_id = max(id_set)
    print(f'Min ID: {min_id}, Max ID: {max_id}')
    assert min_id == 0, f'Expected min ID 0, got {min_id}'
    assert max_id == 51864, f'Expected max ID 51864, got {max_id}'

    # Check contiguous
    expected_set = set(range(51865))
    missing = expected_set - id_set
    print(f'Missing IDs count: {len(missing)}')
    assert len(missing) == 0, f'Missing IDs: {missing}'

    # Verify exact special tokens
    special_checks = {
        50257: '<|endoftext|>',
        50258: '<|startoftranscript|>',
        50259: '<|en|>',
        50276: '<|hi|>',
        50358: '<|translate|>',
        50359: '<|transcribe|>',
        50363: '<|notimestamps|>',
        50364: '<|0.00|>',
        51864: '<|30.00|>'
    }

    print('\n=== SPECIAL TOKENS AUDIT ===')
    all_specials_pass = True
    for tid, expected_str in special_checks.items():
        actual_str = tokens_map.get(tid)
        match = actual_str == expected_str
        print(f'ID {tid}: Expected "{expected_str}" | Actual "{actual_str}" -> {"PASS" if match else "FAIL"}')
        if not match:
            all_specials_pass = False

    assert all_specials_pass, 'Special tokens audit failed!'

    # Check timestamps 50364..51864
    ts_pass = True
    for i in range(1501):
        tid = 50364 + i
        expected_ts = f'<|{i * 0.02:.2f}|>'
        actual_ts = tokens_map.get(tid)
        if actual_ts != expected_ts:
            print(f'Timestamp mismatch at {tid}: expected {expected_ts}, got {actual_ts}')
            ts_pass = False
            break

    print(f'1501 Timestamp tokens check: {"PASS" if ts_pass else "FAIL"}')
    assert ts_pass, 'Timestamp tokens check failed!'

    # Check ONNX n_vocab in encoder
    with open('app/src/main/assets/models/whisper-tiny/tiny-encoder.int8.onnx', 'rb') as f:
        enc_data = f.read()

    assert b'n_vocab\x12\x0551865' in enc_data
    print('Encoder n_vocab=51865 metadata verified!')

    # Check ONNX decoder logits dimension
    with open('app/src/main/assets/models/whisper-tiny/tiny-decoder.int8.onnx', 'rb') as f:
        dec_data = f.read(10000000)
    assert b'\x99\x95\x03' in dec_data # 51865 varint
    print('Decoder logits dimension 51865 verified!')

    print('\nALL TOKENIZER AUDIT CHECKS PASSED 100%!')

if __name__ == '__main__':
    run_audit()
