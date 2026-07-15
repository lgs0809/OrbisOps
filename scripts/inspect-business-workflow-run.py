#!/usr/bin/env python3
"""Inspect an existing browser Workflow run using its SQL checkpoints and independent component evidence."""
import argparse
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]
if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run-id', required=True)
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', args.run_id):
        p.error('supply the actual browser run ID')
    h = runpy.run_path(str(ROOT / 'scripts/test-business-workflow-runtime.py'))
    statements = []
    original_rows = h['inspect'].__globals__['rows']
    def tracked(statement):
        statements.append(statement + ';')
        return original_rows(statement)
    h['inspect'].__globals__['rows'] = tracked
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        result = h['inspect'](args.run_id)
        result['status'] = 'PASS' if result['run']['status'] == 'SUCCEEDED' else 'NOT_PASSED'
    except Exception as error:
        result = {'status': 'NOT_PASSED', 'errorType': type(error).__name__, 'reason': str(error)}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    args.output.with_suffix('.sql').write_text('\n'.join(statements) + '\n')
    print(json.dumps({'status': result['status'], 'runId': args.run_id,
                      'reportStatus': result.get('report', {}).get('status'),
                      'checks': result.get('checks', {}), 'errorType': result.get('errorType')}, ensure_ascii=False))
