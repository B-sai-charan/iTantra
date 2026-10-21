import unicodedata
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

def normalize(text):
    if not text:
        return ''
    nfc = unicodedata.normalize('NFC', text)
    lowered = nfc.lower()
    out = []
    for ch in lowered:
        cat = unicodedata.category(ch)
        if cat.startswith('L') or cat.startswith('M') or cat.startswith('N') or ch.isspace():
            out.append(ch)
    stripped = ''.join(out)
    return re.sub(r'\s+', ' ', stripped).strip()

def count_words(text):
    norm = normalize(text)
    return len(norm.split(' ')) if norm else 0

def count_chars(text):
    norm = normalize(text)
    return len([c for c in norm if not c.isspace()])

# Set A: AsrAccuracyBenchmarkRunner.kt references
benchmark_cases = [
    ('HI-001', 'कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,'),
    ('HI-002', 'मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।'),
    ('HI-003', 'लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.'),
    ('HI-004', 'रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.'),
    ('HI-005', 'यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.')
]

# Set B: docs/asr/HINDI_BENCHMARK_CORPUS.md verbatim references
doc_cases = [
    ('HI-001', 'कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,'),
    ('HI-002', 'मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।'),
    ('HI-003', 'लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.'),
    ('HI-004', 'रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.'),
    ('HI-005', 'यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.')
]

# Set C: The inline strings from the initial sttbench26.1 script attempt
scratch_inline_cases = [
    ('HI-001', 'कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है।'),
    ('HI-002', 'इसमें दो अलग-अलग प्रकार के सेल होते हैं: रॉड और कोन।'),
    ('HI-003', 'इनका उपयोग अक्सर बहरेपन के इलाज के लिए किया जाता है।'),
    ('HI-004', 'कुछ अलग-अलग प्रकार के होते हैं, लेकिन इनमें से अधिकांश में धातु की चादर लगी होती है।'),
    ('HI-005', 'वे कई अन्य प्रजातियों के साथ भोजन और रहने की जगह के लिए भी प्रतिस्पर्धा करते हैं।')
]

print("=== SET A: BENCHMARK SUITE REFERENCES (AsrAccuracyBenchmarkRunner.kt) ===")
tot_w_a = 0
tot_c_a = 0
for cid, ref in benchmark_cases:
    w = count_words(ref)
    c = count_chars(ref)
    tot_w_a += w
    tot_c_a += c
    print(f"  {cid} | words: {w:2d} | chars: {c:2d} | raw: \"{ref}\"")
print(f"Total Words (Set A): {tot_w_a}")
print(f"Total Chars (Set A): {tot_c_a}\n")

print("=== SET B: CORPUS DOCUMENT REFERENCES (HINDI_BENCHMARK_CORPUS.md) ===")
tot_w_b = 0
tot_c_b = 0
for cid, ref in doc_cases:
    w = count_words(ref)
    c = count_chars(ref)
    tot_w_b += w
    tot_c_b += c
    print(f"  {cid} | words: {w:2d} | chars: {c:2d} | raw: \"{ref}\"")
print(f"Total Words (Set B): {tot_w_b}")
print(f"Total Chars (Set B): {tot_c_b}\n")

print("=== SET C: SCRATCH INLINE STRINGS (From early prompt sttbench26 draft) ===")
tot_w_c = 0
tot_c_c = 0
for cid, ref in scratch_inline_cases:
    w = count_words(ref)
    c = count_chars(ref)
    tot_w_c += w
    tot_c_c += c
    print(f"  {cid} | words: {w:2d} | chars: {c:2d} | raw: \"{ref}\"")
print(f"Total Words (Set C): {tot_w_c}")
print(f"Total Chars (Set C): {tot_c_c}\n")
