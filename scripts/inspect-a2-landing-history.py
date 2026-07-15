#!/usr/bin/env python3
"""Read-only isolated A2 approval, all Landing attempts, operation receipts and target state.

Execution keys come from the durable operation journal, not model-prepared arguments.
This never asserts fifteen-minute business acceptance or marks a task complete.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(package_id, output):
    if not re.fullmatch(r'cp-[a-z0-9-]+', package_id):
        raise ValueError('An actual ChangePackage ID is required')
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new evidence path; old observations are preserved')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    package = h['quoted'](package_id)
    queries = {
        'package': "SELECT JSON_OBJECT('id',package_id,'project',project_id,'service',service_id,'status',status,"
                   "'version',version,'hash',package_hash,'approvedVersion',approved_version,'approvedHash',approved_package_hash,"
                   "'creator',create_by,'landingRun',landing_run_id) FROM ai_ops_change_package WHERE package_id=" + package,
        'approvals': "SELECT JSON_OBJECT('actor',approver,'decision',decision,'version',version,'hash',package_hash) "
                     "FROM ai_ops_change_package_approval_record WHERE package_id=" + package + " ORDER BY id",
        'versions': "SELECT JSON_OBJECT('version',version,'hash',package_hash) FROM ai_ops_change_package_version "
                    "WHERE package_id=" + package + " ORDER BY version",
        'attempts': "SELECT JSON_OBJECT('run',run_id,'status',status,'version',approved_version,'hash',approved_package_hash,"
                    "'created',create_time,'finished',finished_at) FROM ai_ops_change_package_landing_run WHERE package_id=" + package + " ORDER BY id",
        'operations': "SELECT JSON_OBJECT('run',landing_run_id,'operation',operation_id,'key',execution_key,"
                      "'status',status,'factStatus',fact_status,'dispatchAttempts',dispatch_attempts,'reason',reason_code,"
                      "'postcheck',IF(JSON_VALID(post_check_result_json),CAST(post_check_result_json AS JSON),NULL),"
                      "'verifiedAt',verified_at) FROM ai_ops_change_package_landing_operation_run WHERE package_id=" + package + " ORDER BY id",
        'authoritativeReceipts': "SELECT JSON_OBJECT('key',d.execution_key,'created',d.created_at,'result',CAST(d.result_json AS JSON)) "
                                 "FROM ops_acceptance_business_a.ops08_deployment_receipt d JOIN ai_ops_change_package_landing_operation_run o "
                                 "ON o.execution_key=d.execution_key WHERE o.package_id=" + package + " ORDER BY d.created_at",
        'actualTarget': "SELECT JSON_OBJECT('service',service_id,'version',version,'scenario',scenario) "
                        "FROM ops_acceptance_business_a.acceptance_service WHERE service_id='ops-acc-a-service-2'"
    }
    proof = {name: h['rows'](sql) for name, sql in queries.items()}
    assert len(proof['package']) == 1
    state = proof['package'][0]
    assert state['project'] == 'ops-acceptance-a' and state['service'] == 'ops-acc-a-service-2'
    approved = [a for a in proof['approvals'] if a['decision'] == 'APPROVE']
    # Some existing ledgers use the terminal adjective rather than the command verb.
    approved += [a for a in proof['approvals'] if a['decision'] == 'APPROVED']
    proof.update(observedAt=datetime.now(timezone.utc).isoformat(), scope='READ_ONLY_LANDING_FACTS',
                 businessAcceptance='NOT_ASSERTED', taskCompletion='NOT_ASSERTED', checks={
                     'approvedIdentityUnchanged': state['version'] == state['approvedVersion'] and state['hash'] == state['approvedHash'],
                     'twoIndependentApprovers': len({a['actor'] for a in approved if a['actor'] != state['creator']
                         and a['version'] == state['version'] and a['hash'] == state['hash']}) >= 2,
                     'allAttemptsBindTheirApprovedVersion': all(
                         any(v['version'] == a['version'] and v['hash'] == a['hash'] for v in proof['versions'])
                         and len({r['actor'] for r in approved if r['version'] == a['version']
                                  and r['hash'] == a['hash'] and r['actor'] != state['creator']}) >= 2
                         for a in proof['attempts']),
                     'actualWriteReceiptCount': len(proof['authoritativeReceipts'])})
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + ';\n'.join(queries.values()) + ';\nCOMMIT;\n')
    print(json.dumps({'packageStatus': state['status'], 'attempts': len(proof['attempts']), 'checks': proof['checks'],
                      'target': proof['actualTarget'], 'businessAcceptance': 'NOT_ASSERTED'}, ensure_ascii=False))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--package-id', required=True)
    p.add_argument('--output', required=True, type=Path)
    a = p.parse_args()
    inspect(a.package_id, a.output)
