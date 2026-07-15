#!/usr/bin/env python3
"""Cross-check an earlier real C acceptance against retained authoritative records.

This does not replay a release or claim a new observation window. Historical remote
HTTP logs remain in the original evidence; current checks use immutable DB receipts.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]


def verify(evidence, acceptance, landing, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Preserve earlier evidence; choose a new output')
    original = json.loads(evidence.read_bytes())
    task = json.loads(acceptance.read_bytes())
    release = json.loads(landing.read_bytes())
    run = original['runId']
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
        raise ValueError('An existing browser-triggered C Run is required')
    if original['package']['project'] != 'ops-acceptance-a':
        raise ValueError('Only the named local acceptance project is supported')
    helper = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    statements = []

    def rows(query):
        statements.append(query + ';')
        return helper['rows'](query)

    quoted = helper['quoted']
    state = rows("SELECT JSON_OBJECT('status',status,'agentId',agent_id,'version',agent_version,"
                 "'hash',agent_definition_hash) FROM ai_ops_agent_run WHERE run_id=" + quoted(run))
    checkpoint = rows("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=" + quoted(run)
                      + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")[0]
    node_outputs = {key.split(':')[1]: value for key, value in checkpoint['state']['variables'].items()
                    if key.startswith('nodeOutput:') and key.count(':') == 1}
    business = next((node_outputs[node]['workflowData_report']
                     for node in ('review-extended', 'unavailable', 'review')
                     if 'workflowData_report' in node_outputs.get(node, {})), {})
    receipts = rows("SELECT JSON_OBJECT('id',result_id,'hash',output_hash,'output',full_output,'status',status) "
                    "FROM ai_ops_tool_result WHERE run_id=" + quoted(run)
                    + " AND source='MCP_REMOTE_TOOL' ORDER BY id")
    saved = {row['id']: row for row in original['receipts']}
    windows = {}
    for phase in ('before', 'after'):
        window = business[phase]['window']
        start, end = [datetime.fromtimestamp(window[key], timezone.utc).strftime('%Y-%m-%d %H:%M:%S')
                      for key in ('startEpoch', 'endEpoch')]
        windows[phase] = rows("SELECT JSON_OBJECT('count',COUNT(*),'uniqueRequests',COUNT(DISTINCT event_id),"
            "'errors',COALESCE(SUM(http_status>=500),0),'versions',GROUP_CONCAT(DISTINCT version)) "
            "FROM ops_acceptance_business_a.ops04_request WHERE service_id=" + quoted(window['serviceId'])
            + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)>=" + quoted(start)
            + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)<" + quoted(end))[0]
    current = [row for row in task['rows'] if row['kind'] == 'episode'][0]
    verified = [row for row in task['rows'] if row['kind'] == 'acceptance'
                and row['id'] == current['verifiedRef']]
    package = release['package'][0]
    checks = {
        'original_real_business_proof_passed': original['crossChecks'] == 'PASS'
            and original['businessVerdict'] == 'PASS' and original['remoteCalls'] != [],
        'frozen_workflow_identity_unchanged': len(state) == 1 and state[0] == {
            key: original['run'][key] for key in ('status', 'agentId', 'version', 'hash')},
        'terminal_c_report_unchanged': 'end' in node_outputs and business == original['businessReport'],
        'c_business_verdict_passed': business['status'] == 'PASS' and not business['evidenceGaps']
            and not business['failedChecks'],
        'all_original_receipts_retained_with_exact_hash': len(receipts) == len(saved) > 0
            and all(row['id'] in saved and row['hash'] == saved[row['id']]['hash']
                    and row['output'] == saved[row['id']]['output']
                    and hashlib.sha256(row['output'].encode()).hexdigest() == row['hash']
                    and row['status'] == 'SUCCEEDED' for row in receipts),
        'two_complete_fifteen_minute_windows': all(business[phase]['window']['complete']
            and business[phase]['window']['endEpoch'] - business[phase]['window']['startEpoch'] == 900
            for phase in windows),
        'independent_request_windows_unchanged': windows == original['independentCompletedRequestWindows']
            and all(row['count'] == row['uniqueRequests'] >= 100 and row['errors'] == 0
                    for row in windows.values()),
        'approved_versions_match_each_request_window':
            windows['before']['versions'] == business['criteria']['baselineVersion']
            and windows['after']['versions'] == business['criteria']['expectedVersion'],
        'current_landing_matches_frozen_c_approval': package['id'] == business['changeRef']['packageId']
            and package['approvedVersion'] == business['changeRef']['approvedVersion']
            and package['approvedHash'] == business['changeRef']['approvedPackageHash']
            and package['landingRun'] == business['changeRef']['landingRunId']
            and package['status'] == 'LANDED',
        'normal_two_person_approval_preserved': release['checks']['approvedIdentityUnchanged']
            and release['checks']['twoIndependentApprovers']
            and release['checks']['allAttemptsBindTheirApprovedVersion'],
        'one_actual_resource_write_retained': len(release['authoritativeReceipts']) == 1,
        'task_acceptance_still_current_and_succeeded': task['status'] == 'PASS_INTEGRITY'
            and current['outcome'] == 'SUCCEEDED' and len(verified) == 1
            and verified[0]['sourceRun'] == run and verified[0]['checks'] == 11,
        'actual_current_target_matches_accepted_version': release['actualTarget'] == [{
            'service': business['criteria']['serviceId'],
            'version': business['criteria']['expectedVersion'], 'scenario': 'HEALTHY'}],
    }
    result = {'status': 'PASS_RETAINED_REAL_BUSINESS_ACCEPTANCE' if all(checks.values()) else 'FAIL',
              'observedAt': datetime.now(timezone.utc).isoformat(), 'runId': run,
              'packageId': package['id'], 'episodeId': task['episodeId'], 'checks': checks,
              'independentCompletedRequestWindows': windows,
              'inputs': [{'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                         for path in (evidence, acceptance, landing)],
              'boundary': 'Retained real browser/model/approval/Landing/C/task acceptance, checked read-only. '
                          'No release replay, new time window or new business-source count asserted.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + '\n'.join(statements) + '\nCOMMIT;\n')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'checks': len(checks),
                      'failed': [key for key, passed in checks.items() if not passed]}))
    return 0 if all(checks.values()) else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('evidence', 'acceptance', 'landing', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    raise SystemExit(verify(args.evidence, args.acceptance, args.landing, args.output))
