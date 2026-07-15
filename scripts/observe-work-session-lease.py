#!/usr/bin/env python3
"""Read-only lease timeline for one local acceptance run; never exports prompts or tokens."""
import argparse
import json
from pathlib import Path
import runpy
import time


def main(run_id, output, seconds):
    h = runpy.run_path(str(Path(__file__).resolve().with_name('test-mcp-runtime.py')))
    run = h['quoted'](run_id)
    query = """SELECT JSON_OBJECT('runId',run_id,'status',status,'attempt',current_attempt_id,
      'cancelRequested',cancel_requested,'leaseExpiresAt',lease_expires_at,
      'databaseNow',CURRENT_TIMESTAMP(3),'leaseRemainingSeconds',
      TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP(3),lease_expires_at),'error',error_message)
      FROM ai_ops_agent_run WHERE run_id=""" + run
    trace = """SELECT JSON_OBJECT('sequence',sequence_no,'event',event_type,'status',status)
      FROM ai_ops_agent_node_trace WHERE run_id=""" + run + " ORDER BY sequence_no DESC LIMIT 5"
    receipt = "SELECT JSON_OBJECT('productionDeploymentReceipts',COUNT(*)) FROM ops_acceptance_business_a.ops08_deployment_receipt"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query + ';\n' + trace + ';\n' + receipt + ';\n')
    proof = {'runId': run_id, 'readOnly': True, 'samples': []}
    deadline = time.monotonic() + seconds
    while True:
        rows = h['rows'](query)
        proof['samples'].append({'run': rows, 'latestEvents': h['rows'](trace), 'resources': h['rows'](receipt)})
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
        if rows and rows[0]['status'] in ('SUCCEEDED', 'FAILED', 'CANCELED', 'WAITING_APPROVAL'):
            break
        if time.monotonic() >= deadline:
            break
        time.sleep(min(10, max(0, deadline-time.monotonic())))
    print(json.dumps({'samples': len(proof['samples']), 'lastRun': rows}, ensure_ascii=False))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run-id', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--seconds', type=int, default=600, choices=range(0, 3601), metavar='0..3600')
    args = p.parse_args()
    main(args.run_id, args.output, args.seconds)
