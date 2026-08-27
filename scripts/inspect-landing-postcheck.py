#!/usr/bin/env python3
"""Cross-check a persisted independent Landing postcheck against immutable receipts and real requests.

Read-only. Does not approve, execute, replay, complete a task, or assert SLO window acceptance.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(run_id, output, service=None):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new output path to retain previous evidence')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    queries = []
    def rows(query):
        queries.append(query + ';')
        return h['rows'](query)
    q = h['quoted']
    report = {'scope': 'INDEPENDENT_POSTCHECK_NOT_SLO_ACCEPTANCE', 'runId': run_id}
    try:
        verification = rows("SELECT JSON_OBJECT('id',verification_id,'run',landing_run_id,'project',project_id,"
            "'package',package_id,'version',approved_version,'hash',approved_package_hash,'passed',passed,"
            "'proof',CAST(proof_json AS JSON)) FROM ai_ops_landing_verification WHERE landing_run_id="
            + q(run_id) + " ORDER BY id DESC LIMIT 1")[0]
        report['verification'] = verification
        target = None
        if service:
            target = runpy.run_path(str(ROOT / 'scripts/inspect-platform-resource.py'))['inspect'](
                verification['project'], service, 'prod', output.with_name(output.stem + '-resource.json'))
            report['nativeResourceEvidence'] = {'path': str(output.with_name(output.stem + '-resource.json')),
                'sha256': hashlib.sha256(output.with_name(output.stem + '-resource.json').read_bytes()).hexdigest(),
                'resourceKey': target['resourceKey']}
        package = rows("SELECT JSON_OBJECT('version',approved_version,'hash',approved_package_hash,'run',landing_run_id) "
            "FROM ai_ops_change_package WHERE package_id=" + q(verification['package']))[0]
        assert all(package[k] == verification[k] for k in ('version', 'hash', 'run'))
        def leaves(checks):
            for check in checks:
                if 'checks' in check:
                    yield from leaves(check['checks'])
                else:
                    yield check
        evidence = []
        for check in leaves(verification['proof']['checks']):
            receipt = check.get('readReceipt')
            if not receipt:
                continue
            full = rows("SELECT JSON_OBJECT('run',run_id,'hash',output_hash,'output',full_output) FROM ai_ops_tool_result "
                "WHERE result_id=" + q(receipt['providerResultId']))[0]
            assert full['run'] == run_id
            assert full['hash'] == receipt['providerOutputHash'] == hashlib.sha256(full['output'].encode()).hexdigest()
            assert json.loads(full['output'])['normalizedContent'] == receipt['normalizedContent']
            requests = receipt['normalizedContent'].get('requests', [])
            traces = [item['traceId'] for item in requests]
            observed = []
            if traces:
                assert len(set(traces)) == len(traces)
                if target:
                    fields = {'traceId': 'trace_id', 'httpStatus': 'status', 'version': 'version',
                              'durationMs': 'duration_ms'}
                    raw = [r for r in target['resourceFacts']['request'] if r['trace_id'] in traces]
                    assert len(raw) == len(traces)
                    ordered = sorted(raw, key=lambda r: r['observed_at'])
                    groups = {}
                    for audit in target['resourceFacts']['audit_request']:
                        if audit['resource_key'] == target['resourceKey'] and audit['project_id'] == target['projectId'] \
                                and audit['path'] == '/orders' and audit['method'] == 'GET':
                            groups.setdefault(audit['rpc_id'], []).append(audit)
                    observed_end = dt.datetime.fromisoformat(receipt['normalizedContent']['observedAt']).timestamp()
                    matches = []
                    for group in groups.values():
                        group = sorted(group, key=lambda r: r['received_at'])
                        if len(group) != len(ordered): continue
                        if all(a['received_at'] <= r['observed_at'] <= (
                                group[i+1]['received_at'] if i+1 < len(group) else observed_end)
                                and a['http_status'] == r['status'] for i, (a, r) in enumerate(zip(group, ordered))):
                            matches.append(group)
                    assert len(matches) == 1, 'Exact scoped sequential HTTP audit group missing or ambiguous'
                    observed = [{**{k: r[fields[k]] for k in requests[0] if k in fields},
                                 'method': a['method'], 'path': a['path']}
                                for r, a in zip(ordered, matches[0])]
                    report.setdefault('nativeRequestAuditGroups', []).append(matches[0])
                else:
                    observed = rows("SELECT JSON_OBJECT('traceId',event_id,'httpStatus',http_status,'version',version) "
                        "FROM ops_acceptance_business_a.ops04_request WHERE event_id IN (" + ','.join(map(q, traces)) + ")")
                assert sorted(requests, key=lambda x: x['traceId']) == sorted(observed, key=lambda x: x['traceId'])
            if target:
                assert receipt['normalizedContent'].get('resourceKey', target['resourceKey']) == target['resourceKey']
                assert receipt['normalizedContent']['projectId'] == target['projectId']
            evidence.append({'receipt': receipt, 'requestRows': observed})
        report['evidence'] = evidence
        if target:
            operations = rows("SELECT JSON_OBJECT('executionKey',execution_key,'resourceKey',resource_key,"
                "'status',status,'resultId',result_id,'outputHash',output_hash) "
                "FROM ai_ops_change_package_landing_operation_run WHERE landing_run_id=" + q(run_id))
            assert operations and all(o['status'] == 'SUCCEEDED' and o['resourceKey'] == target['resourceKey'] for o in operations)
            report['writeReceipts'] = []
            for operation in operations:
                matched = [r for r in target['resourceFacts']['receipt'] if r['execution_key'] == operation['executionKey']]
                assert len(matched) == 1
                native = json.loads(matched[0]['body_json'])
                full = rows("SELECT JSON_OBJECT('run',run_id,'hash',output_hash,'output',full_output) "
                    "FROM ai_ops_tool_result WHERE result_id=" + q(operation['resultId']))[0]
                assert full['run'] == run_id and full['hash'] == operation['outputHash'] == hashlib.sha256(full['output'].encode()).hexdigest()
                assert json.loads(full['output'])['normalizedContent'] == native
                report['writeReceipts'].append({'operation': operation, 'nativeReceipt': native})
        else:
            report['writeReceipts'] = rows("SELECT JSON_OBJECT('executionKey',d.execution_key,'result',CAST(d.result_json AS JSON)) "
                "FROM ops_acceptance_business_a.ops08_deployment_receipt d JOIN ai_ops_change_package_landing_operation_run o "
                "ON o.execution_key=d.execution_key WHERE o.landing_run_id=" + q(run_id))
        report['integrity'] = 'PASS'
        report['postcheck'] = 'PASS' if verification['passed'] == 1 else 'FAIL'
    except Exception as error:
        report.update(integrity='FAIL', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + '\n'.join(queries) + '\nCOMMIT;\n')
    print(json.dumps({'integrity': report['integrity'], 'postcheck': report['postcheck'],
        'checkedRequests': sum(len(r['requestRows']) for r in evidence), 'writeReceiptCount': len(report['writeReceipts'])}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--service', help='Exact isolated SQLite target service; reads native ledger without business requests')
    args = parser.parse_args()
    inspect(args.run_id, args.output, args.service)
