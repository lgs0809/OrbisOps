#!/usr/bin/env python3
"""Read-only acceptance of the agreed isolated A2 recovery/release contract, not generic production policy.

Saves the actual package, SQL and target facts. Never approves, edits a package,
changes a resource, or equates a valid plan with successful Landing/business acceptance.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'
SERVICE = 'ops-acc-a-service-2'
RESOURCE = 'service://' + SERVICE + '/prod'


def inspect(package_id, output, change_kind='RECOVERY', baseline_version='fixture-1', expected_version='fixture-2'):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new output path; previous observations are preserved')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    package = h['api']('/api/v1/user/ops/change-packages/' + package_id, token=h['token'])
    fields = ('packageId', 'projectId', 'serviceId', 'status', 'version', 'packageHash',
              'createBy', 'approveBy', 'approvedVersion', 'approvedPackageHash', 'landingRunId',
              'mcpSteps', 'verificationCriteriaJson', 'preflightResultJson', 'dryRunResultJson',
              'approvalBoundaryJson', 'landingResult', 'validationAssessment')
    proof = {'scope': 'ISOLATED_A2_' + change_kind + '_CONTRACT', 'package': {k: package.get(k) for k in fields},
             'checkedAt': datetime.now(timezone.utc).isoformat(), 'checks': [],
             'businessAcceptance': 'NOT_ASSERTED'}
    statements = []

    def rows(statement):
        statements.append(statement)
        return h['rows'](statement)

    def check(name, passed):
        proof['checks'].append({'name': name, 'passed': bool(passed)})

    def decoded(value):
        return json.loads(value) if isinstance(value, str) else value

    try:
        check('package targets isolated A2 production', package.get('projectId') == PROJECT
              and package.get('serviceId') == SERVICE and package.get('targetEnvironment') == 'prod')
        check('package has not been rejected or invalidated', package.get('status') in
              ('READY_FOR_REVIEW', 'REVIEWING', 'APPROVED', 'LANDING_RUNNING', 'LANDED'))
        writes = [step for step in package.get('mcpSteps', []) if step.get('writesTargetResource') is True]
        check('exactly one production write', len(writes) == 1)
        write = writes[0] if len(writes) == 1 else {}
        args = write.get('arguments', {})
        check('reviewed bounded configuration operation', write.get('toolName') == 'prod_apply_configuration'
              and write.get('resourceScope') == RESOURCE and write.get('reviewStatus') == 'HUMAN_REVIEWED'
              and write.get('requiresApproval') is True and write.get('prepareAllowed') is False)
        check('CAS from ' + baseline_version + ' to ' + expected_version + ' HEALTHY', all(args.get(k) == v for k, v in
              {'projectId': PROJECT, 'service': SERVICE, 'expectedVersion': baseline_version,
               'version': expected_version, 'scenario': 'HEALTHY'}.items()))
        check('idempotent write identity', isinstance(args.get('executionKey'), str) and bool(args['executionKey']))
        primary = write.get('postCheck', {})
        checks = [primary] + primary.get('additionalChecks', []) if isinstance(primary, dict) else []
        for tool, expected in (
                ('prod_read_state', {'version': expected_version, 'scenario': 'HEALTHY'}),
                ('prod_check_orders', {'requestCount': 20, 'errorCount': 0})):
            matches = [item for item in checks if item.get('toolName') == tool]
            check(tool + ' has an attached mandatory result assertion', len(matches) == 1 and
                  all(matches[0].get('expectedValues', {}).get(k) == v for k, v in expected.items()))
        check('every postcheck uses the correct project and resource', bool(checks) and all(
              item.get('arguments', {}).get('projectId') == PROJECT
              and item.get('arguments', {}).get('service') == SERVICE
              and item.get('expectedValues', {}).get(item.get('resourceIdentityField', 'resourceKey')) == RESOURCE
              for item in checks))
        check('failure and recovery instructions retained', all(isinstance(write.get(k), dict)
              and bool(write[k]) for k in ('preconditions', 'rollbackPlan', 'rollbackPrecondition', 'manualFallback')))
        criteria = decoded(package.get('verificationCriteriaJson')) or []
        slo = [item for item in criteria if item.get('kind') == 'OBSERVABILITY_SLO_V1']
        expected_slo = {'changeKind': change_kind, 'serviceId': SERVICE, 'environment': 'prod',
                        'resourceIdentity': RESOURCE, 'baselineVersion': baseline_version, 'expectedVersion': expected_version,
                        'collectionDefinition': 'ops04-completed-http-raw-scrapes-v1', 'routeDefinition': '/orders/id',
                        'minQps': 0.5, 'maxQps': 2, 'maxErrorRate': 0.01, 'maxP95Seconds': 1}
        check('approved ' + change_kind + ' SLO contract', len(slo) == 1 and all(slo[0].get(k) == v for k, v in expected_slo.items()))
        check('actual test validation evidence', (decoded(package.get('dryRunResultJson')) or {}).get('verified') is True)
        proof['persistedPackage'] = rows("SELECT JSON_OBJECT('id',package_id,'status',status,'version',version,"
              "'hash',package_hash,'approvedVersion',approved_version,'approvedHash',approved_package_hash,"
              "'creator',create_by,'approver',approve_by,'landingRunId',landing_run_id) "
              "FROM ai_ops_change_package WHERE package_id=" + h['quoted'](package_id))
        check('API matches durable package identity', len(proof['persistedPackage']) == 1
              and proof['persistedPackage'][0]['hash'] == package.get('packageHash')
              and proof['persistedPackage'][0]['version'] == package.get('version'))
        proof['target'] = rows("SELECT JSON_OBJECT('version',version,'scenario',scenario) FROM "
                              "ops_acceptance_business_a.acceptance_service WHERE service_id=" + h['quoted'](SERVICE))
        proof['writeReceipts'] = rows("SELECT JSON_OBJECT('key',execution_key,'result',CAST(result_json AS JSON)) "
              "FROM ops_acceptance_business_a.ops08_deployment_receipt WHERE execution_key=" + h['quoted'](args.get('executionKey', '')))
        if package.get('approvedVersion'):
            check('approval binds the current package and independent reviewer', package.get('approvedVersion') == package.get('version')
                  and package.get('approvedPackageHash') == package.get('packageHash')
                  and bool(package.get('approveBy')) and package.get('approveBy') != package.get('createBy'))
        proof['status'] = 'PASS_PLAN_CONTRACT_ONLY' if all(c['passed'] for c in proof['checks']) else 'FAIL_PLAN_CONTRACT'
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text(';\n'.join(statements) + ';\n')
    print(json.dumps({'status': proof['status'], 'packageStatus': package.get('status'),
                      'failedChecks': [c['name'] for c in proof['checks'] if not c['passed']],
                      'writeReceipts': len(proof.get('writeReceipts', []))}, ensure_ascii=False))
    return proof['status'] == 'PASS_PLAN_CONTRACT_ONLY'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--change-kind', choices=('RECOVERY', 'RELEASE'), default='RECOVERY')
    parser.add_argument('--baseline-version', default='fixture-1')
    parser.add_argument('--expected-version', default='fixture-2')
    args = parser.parse_args()
    raise SystemExit(0 if inspect(args.package_id, args.output, args.change_kind,
                                 args.baseline_version, args.expected_version) else 1)
