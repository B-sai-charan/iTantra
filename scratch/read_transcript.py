import json

with open(r'C:/Users/saich/.gemini/antigravity-ide\brain\80df07f0-2cc7-4417-86f8-ed97f41c3c85/.system_generated/logs/transcript_full.jsonl', 'r', encoding='utf-8') as f:
    for i, line in enumerate(f):
        if i >= 520:
            data = json.loads(line)
            print(f'Line {i}: Type: {data.get("type")}, Source: {data.get("source")}, Created: {data.get("created_at")}')
            if data.get('type') == 'PLANNER_RESPONSE':
                for tc in data.get('tool_calls', []):
                    print(f'   Call: {tc}')
                if data.get('content'):
                    print(f'   Content excerpt: {data.get("content")[:150]}...')
            elif data.get('type') == 'RUN_COMMAND':
                print(f'   RunCmd output: {data.get("content", "")[:200]}')
            elif data.get('type') == 'SYSTEM_MESSAGE':
                print(f'   SysMsg: {data.get("content", "")[:200]}')
