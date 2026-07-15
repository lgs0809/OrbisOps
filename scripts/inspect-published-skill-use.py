#!/usr/bin/env python3
"""Audit actual frozen Skill/resource reads separately from task outcome; never mutates runtime records."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def inspect(run_id, skill_id, output):
    if output.exists():
        raise ValueError('Choose a new evidence path; retain earlier results')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = support['quoted']
    statements = {
        'run': "SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'status',status,'error',error_message) FROM ai_ops_agent_run WHERE run_id=" + q(run_id),
        'bundles': "SELECT JSON_OBJECT('hash',bundle_hash,'bundle',bundle_json) FROM ai_ops_runtime_context_bundle WHERE run_id=" + q(run_id),
        'receipts': "SELECT JSON_OBJECT('id',result_id,'source',source,'status',status,'hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE run_id=" + q(run_id) + ' ORDER BY id',
        'events': "SELECT JSON_OBJECT('type',event_type,'payload',payload_json) FROM ai_ops_agent_node_trace WHERE run_id=" + q(run_id) + " AND event_type IN ('MODEL_RESPONSE_VERIFIED','MODEL_CALL_RETRYING') ORDER BY id",
    }
    data = {name: support['rows'](sql) for name, sql in statements.items()}
    if not data['run'] or data['run'][0]['projectId'] != 'ops-acceptance-a':
        raise ValueError('An actual isolated acceptance Run is required')
    references = []
    for bundle in data['bundles']:
        document = json.loads(bundle.pop('bundle'))
        references.extend(x for x in document.get('skillCatalogRefs', []) if x.get('skillId') == skill_id)
        bundle['catalogRefs'] = document.get('skillCatalogRefs', [])
        bundle['selectedRefs'] = document.get('usedSkillVersionRefs', [])
    hashes = bool(data['receipts']) and all(hashlib.sha256(x['output'].encode()).hexdigest() == x['hash'] for x in data['receipts'])
    method_reads, remote = [], []
    for receipt in data['receipts']:
        value = json.loads(receipt['output'])
        if value.get('skillId') == skill_id:
            artifact = value.get('artifact', {})
            if artifact.get('path') == 'resources/method.json':
                method_reads.append({'receiptId': receipt['id'], 'version': value.get('version'),
                    'packageHash': value.get('packageHash'), 'artifactHash': artifact.get('contentHash'),
                    'contentHashMatches': hashlib.sha256(artifact.get('content', '').encode()).hexdigest() == artifact.get('contentHash'),
                    'frozenReferenceMatches': any(x.get('version') == value.get('version') and x.get('packageHash') == value.get('packageHash')
                        and x.get('artifactHashes', {}).get('resources/method.json') == artifact.get('contentHash') for x in references)})
        if receipt['source'] == 'MCP_REMOTE_TOOL':
            observed = value.get('normalizedContent', {})
            remote.append({'receiptId': receipt['id'], 'observed': observed})
    request = urllib.request.Request('http://127.0.0.1:18862/evidence',
        headers={'Authorization': 'Bearer ' + support['values']['OPS_ACCEPTANCE_OBSERVABILITY_TOKEN']})
    with urllib.request.urlopen(request, timeout=15) as response:
        provider = json.load(response)
    checks = []
    for item in remote:
        observed = item['observed']
        record = next((r for r in provider['queries'] if r['query_id'] == observed.get('queryId')), None)
        checks.append({'receiptId': item['receiptId'], 'queryId': observed.get('queryId'), 'kind': observed.get('kind'),
            'scope': observed.get('scope'), 'providerMatches': bool(record) and record['response_sha256']
                == hashlib.sha256(json.dumps(observed, sort_keys=True).encode()).hexdigest()})
    models = [json.loads(e['payload']) for e in data['events'] if e['type'] == 'MODEL_RESPONSE_VERIFIED']
    passed = bool(method_reads) and hashes and all(r['contentHashMatches'] and r['frozenReferenceMatches'] for r in method_reads)
    passed = passed and bool(models) and all(r.get('requestedModel') == r.get('responseModel')
        and r.get('responseModel') in ('gpt-5.6-luna', 'gpt-5.6-terra') for r in models)
    summary = {'status': 'PASS_ACTUAL_METHOD_READ' if passed else 'NOT_PASSED_METHOD_READ', 'run': data['run'],
        'frozenReferences': references, 'methodReads': method_reads, 'allReceiptHashesMatch': hashes,
        'providerCrossChecks': checks, 'modelsVerified': len(models),
        'boundary': 'Actual complete method/resource reads only. Business acceptance is separate; ranking and entry-only reads do not count.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(statements.values()) + ';\n')
    output.with_name(output.stem + '-records.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: summary[k] for k in ('status', 'run', 'methodReads', 'allReceiptHashesMatch')}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--skill-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.run_id, args.skill_id, args.output)
