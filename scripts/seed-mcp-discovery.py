#!/usr/bin/env python3
"""Register the separate 24-tool, SQLite-backed discovery fixture through audited APIs.

Use local-acceptance.py's compose helper with --profile discovery to build/start mcp-discovery.
Only the fixed loopback endpoint is accepted. Existing changed/disabled policies are preserved.
--approve-read-only uses the existing acceptance admin to review these inspected read-only tools.
"""
import argparse
import json
from pathlib import Path
import runpy
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'
NAME = 'OPS-03 大目录只读验收'
ENDPOINT = 'http://127.0.0.1:8481/mcp'
TOOLS = {f'service_{i:02}_status' for i in range(1, 25)}


def prepare(approve, output):
    if output.exists():
        raise ValueError('Use a new evidence filename')
    support = runpy.run_path(str(ROOT / 'scripts/seed-local-acceptance.py'))
    credentials = json.loads((ROOT / 'deploy/.acceptance-private/admin.json').read_text())
    api, token = support['request'], support['login'](credentials)
    path = f'/api/v1/admin/ops/projects/{PROJECT}'
    def integration():
        found = [item for item in api(path + '/tools', token=token) if item.get('mcpName') == NAME]
        if len(found) > 1:
            raise RuntimeError('Ambiguous fixture identity; retained')
        return found[0] if found else None
    mcp = integration()
    if not mcp:
        actor = next(item for item in api('/api/v1/admin/admin-user/query-all', token=token)
                     if item['username'] == credentials['username'])
        api('/api/v1/admin/ops/tool-executions', 'POST', {
            'projectId': PROJECT, 'toolsetId': 'capability.manage', 'toolName': 'mcp_server_import',
            'userId': actor['userId'], 'authenticatedUsername': credentials['username'],
            'executionScope': 'PRE_APPROVAL_WORKFLOW', 'runId': 'ops-discovery-import-' + uuid.uuid4().hex,
            'arguments': {'sourceUrl': ENDPOINT, 'capabilityName': NAME,
                          'credentialRef': '${env:OPS_ACCEPTANCE_MCP_TOKEN}', 'transportType': 'streamable-http'}}, token)
        mcp = integration()
    if not mcp or mcp.get('status') not in ('PENDING_REVIEW', 'ENABLED'):
        raise RuntimeError('Fixture missing or disabled; retained')
    if mcp['transportConfig'].get('endpoint') != ENDPOINT or {item['toolName'] for item in mcp['remoteTools']} != TOOLS:
        raise RuntimeError('Fixture endpoint or complete catalog changed; retained')
    policies = [item for item in api(path + '/mcp-tool-policies', token=token) if item['mcpId'] == mcp['mcpId']]
    reviewed = []
    for tool in mcp['remoteTools']:
        policy = next(item for item in policies if item['toolName'] == tool['toolName'] and item['schemaHash'] == tool['schemaHash'])
        if policy['status'] not in ('PENDING_REVIEW', 'ACTIVE'):
            raise RuntimeError('Disabled/rejected policy retained')
        if policy.get('reviewStatus') != 'HUMAN_REVIEWED':
            if not approve:
                raise RuntimeError('Read-only fixture review required; rerun with --approve-read-only')
            policy = api(path + '/mcp-tool-policies/' + policy['policyId'] + '/approve', 'POST', {
                'effectType': 'READ_EXTERNAL_STATE', 'effectScope': 'TARGET_RESOURCE_READ', 'mutability': 'READ_ONLY',
                'capability': 'READ_ONLY', 'allowedActions': [tool['toolName'].upper()], 'riskLevel': 'LOW',
                'readOnly': True, 'investigateAllowed': True, 'prepareAllowed': False, 'landAllowed': False,
                'requiresApprovedPackage': False, 'requiresHumanApproval': False, 'requiresDryRun': False,
                'requiresRollbackPlan': False, 'disclosureTier': 'CORE',
                'reason': '本机隔离目录验收；已审阅固定工具白名单、参数化 SQLite SELECT 与关联种子记录，仅允许读取夹具服务状态。'}, token)
        if policy['status'] != 'ACTIVE' or policy['readOnly'] is not True or policy['landAllowed'] is not False:
            raise RuntimeError('Reviewed policy differs; retained')
        reviewed.append({key: policy.get(key) for key in ('policyId','toolName','schemaHash','reviewedBy','reviewedAt','status','readOnly')})
    if mcp['status'] != 'ENABLED':
        api(path + '/tools/' + mcp['mcpId'] + '/status', 'PATCH', {'status':'ENABLED'}, token)
    result = dict(status='READY', projectId=PROJECT, mcpId=mcp['mcpId'], endpoint=ENDPOINT, reviewedPolicies=reviewed)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(dict(status='READY', mcpId=mcp['mcpId'], tools=len(reviewed)), ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--approve-read-only', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.approve_read_only, args.output)
