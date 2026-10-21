import sys
sys.path.append('scratch')
from run_controlled_same_sentence_07 import calculate_wer_cer

ref12 = 'After early nightfall, the yellow lamps would light up here and there.'
ref18 = 'After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels.'

for name, hyp in [
    ('Reference Clean Audio', 'After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels.'),
    ('Live Microphone Audio', 'I need to go to the other side'),
    ('Level-Matched Live Mic Audio', 'I need to get the standard standard')
]:
    w12, c12, r12, h = calculate_wer_cer(ref12, hyp)
    w18, c18, r18, h = calculate_wer_cer(ref18, hyp)
    print(f'=== {name} ===')
    print(f'  Hyp: "{hyp}"')
    print(f'  12-word Ref WER: {w12*100:.2f}%, CER: {c12*100:.2f}%')
    print(f'  18-word Ref WER: {w18*100:.2f}%, CER: {c18*100:.2f}%')
