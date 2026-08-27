#!/usr/bin/env python3
"""Verify an actual isolated A2 Landing postcheck against immutable receipts and HTTP request rows.

Read-only. This immediate check is distinct from the later fifteen-minute SLO acceptance.
"""
import argparse
import json
from pathlib import Path
import re
import runpy

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--run-id', required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
if not re.fullmatch(r'lr-[a-z0-9-]+', a.run_id) or a.output.exists():
    p.error('Use an actual Landing run ID and a new evidence path')
h = runpy.run_path(str(Path(__file__).resolve().parent / 'test-mcp-runtime.py'))
queries = []
def rows(sql):
    queries.append(sql)
    return h['rows'](sql)
run = h['quoted'](a.run_id)
result = rows("SELECT JSON_OBJECT('status',status,'verification',JSON_EXTRACT(result_json,'$.independentVerification')) "
              "FROM ai_ops_change_package_landing_run WHERE run_id=" + run)[0]
assert result['status'] == 'SUCCEEDED' and result['verification']['passed'] is True
checks = [c for operation in result['verification']['checks'] for c in operation['checks']]
orders = [c['readReceipt'] for c in checks if c['readReceipt']['remoteToolName'] == 'prod_check_orders']
assert len(orders) == 1 and all(c['passed'] is True for c in checks)
receipt = orders[0]
actual = receipt['normalizedContent']
assert actual['projectId'] == 'ops-acceptance-a' and actual['environment'] == 'prod'
assert actual['resourceKey'] == 'service://ops-acc-a-service-2/prod'
assert actual['requestCount'] == 20 and actual['errorCount'] == 0 and len(actual['requests']) == 20
assert all(r['httpStatus'] == 200 and r['version'] == 'fixture-2' for r in actual['requests'])
receipt_id = h['quoted'](receipt['providerResultId'])
saved = rows("SELECT JSON_OBJECT('id',result_id,'hash',output_hash,'hashMatches',SHA2(full_output,256)=output_hash,"
             "'source',source,'status',status) FROM ai_ops_tool_result WHERE result_id=" + receipt_id)
assert len(saved) == 1 and saved[0]['hashMatches'] == 1 and saved[0]['source'] == 'MCP_REMOTE_TOOL'
assert saved[0]['status'] == 'SUCCEEDED' and saved[0]['hash'] == receipt['providerOutputHash']
trace_ids = {r['traceId'] for r in actual['requests']}
assert len(trace_ids) == 20
requests = rows("SELECT JSON_OBJECT('traceId',event_id,'status',http_status,'version',version,'service',service_id) "
                "FROM ops_acceptance_business_a.ops04_request WHERE event_id IN ("
                + ','.join(h['quoted'](t) for t in sorted(trace_ids)) + ')')
assert {r['traceId'] for r in requests} == trace_ids
assert all(r['status'] == 200 and r['version'] == 'fixture-2' and r['service'] == 'ops-acc-a-service-2' for r in requests)
proof = {'status': 'PASS', 'scope': 'REAL_IMMEDIATE_INDEPENDENT_POSTCHECK', 'run': a.run_id,
         'businessWindowAcceptance': 'NOT_ASSERTED', 'landing': result, 'receipt': saved, 'actualRequests': requests}
a.output.parent.mkdir(parents=True, exist_ok=True)
a.output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
a.output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + ';\n'.join(queries) + ';\nCOMMIT;\n')
print(json.dumps({'status': 'PASS', 'scope': proof['scope'], 'matchedActualRequests': len(requests)}))
