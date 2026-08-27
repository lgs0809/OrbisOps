#!/usr/bin/env python3
"""Read the retained run, remote calls, receipts and events without replaying work."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main(run_id, output):
    if output.exists():
        raise ValueError('Prior evidence retained; choose a fresh output path')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    quoted = h['quoted'](run_id)
    runs = h['rows']("SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'userId',user_id,'status',status,'version',agent_version,"
        "'hash',agent_definition_hash,'epoch',fencing_token,'error',error_message) "
        "FROM ai_ops_agent_run WHERE run_id=" + quoted)
    if len(runs) != 1:
        raise ValueError('Exactly one actual retained run is required')
    calls = h['rows']("SELECT JSON_OBJECT('tool',tool_name,'readOnly',read_only,'status',status,"
        "'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id=" + quoted + ' ORDER BY id')
    receipts = h['rows']("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'output',full_output,"
        "'status',status,'source',source) FROM ai_ops_tool_result WHERE run_id=" + quoted + ' ORDER BY id')
    events = h['api']('/api/v1/admin/ops-agent-runs/' + run_id + '/events/list', token=h['token'])
    jar = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
        'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0]
    result = {'recordedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'runId': run_id,
        'collectorSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'deployedJarSha256': jar, 'nativeRun': runs[0], 'toolCalls': calls,
        'receipts': receipts, 'events': events, 'newExecutions': 0,
        'boundary': 'Retained execution evidence only; no TaskAcceptance or business-success verdict.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    output.with_suffix('.sql').write_text('\n'.join(h['sql_queries']) + '\n')
    print(json.dumps({'nativeRun': runs[0], 'toolCallCount': len(calls),
        'receiptCount': len(receipts), 'output': str(output)}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    main(args.run_id, args.output)
