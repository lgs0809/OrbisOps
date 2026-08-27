#!/usr/bin/env python3
"""Reconcile the isolated budget-denial fixture via the normal audited API, never SQL mutation."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'
RUN = 'lr-bf62d57b-0ffc-4069-91a2-3f1e91940c0f'
ERROR = 'MCP_AUTHORITY_DENIED:WORKFLOW_TOOL_DISPATCH_DENIED:WORKFLOW_TOOL_BUDGET_DEFINITION_MISSING'


def reconcile(output):
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    endpoint = '/api/v1/admin/ops/tool-execution-reconciliation'
    unresolved = h['api'](endpoint + '/unresolved?projectId=' + PROJECT + '&runId=' + RUN, token=h['token'])
    if not unresolved:
        print(json.dumps({'status': 'NO_UNRESOLVED_RECORD', 'runId': RUN}))
        return
    assert len(unresolved) == 1 and unresolved[0]['errorMessage'] == ERROR
    context = unresolved[0]['reconciliationContext']
    assert context['remoteToolName'] == 'prod_apply_configuration'
    assert context['changePackageId'] == 'cp-28624d63-9117-450f-907e-f55a9fc9117e'
    queries = [
        "SELECT JSON_OBJECT('tool',tool_name,'status',status,'output',output_json) FROM ai_ops_mcp_tool_call WHERE run_id="
        + h['quoted'](RUN) + " AND tool_name='prod_apply_configuration'",
        "SELECT JSON_OBJECT('version',version,'scenario',scenario,'receipts',"
        "(SELECT COUNT(*) FROM ops_acceptance_business_a.ops08_deployment_receipt)) "
        "FROM ops_acceptance_business_a.acceptance_service WHERE service_id='ops-acc-a-service-2'",
    ]
    calls, state = [h['rows'](query) for query in queries]
    assert len(calls) == 1 and calls[0]['status'] == 'FAILED'
    failure = json.loads(calls[0]['output'])
    assert failure['dispatched'] is False and failure['message'] == ERROR
    assert state == [{'version': 'fixture-1', 'scenario': 'FAULT', 'receipts': 0}]
    proof = {'runId': RUN, 'unresolved': unresolved, 'providerFailure': calls, 'actualResource': state,
             'conclusion': 'CONFIRMED_NOT_EXECUTED', 'basis': 'Pre-transport denial plus unchanged local resource and zero atomic receipts'}
    raw = json.dumps(proof, ensure_ascii=False, indent=2) + '\n'
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise RuntimeError('Existing evidence preserved; choose another output file')
    output.write_text(raw)
    output.with_suffix('.sql').write_text(';\n'.join(queries) + ';\n')
    result = h['api'](endpoint + '/resolve', 'POST', {
        'projectId': PROJECT, 'runId': RUN, 'idempotencyKey': unresolved[0]['idempotencyKey'],
        'resolution': 'CONFIRMED_NOT_EXECUTED', 'evidenceId': output.name,
        'evidenceHash': hashlib.sha256(raw.encode()).hexdigest(),
        'note': '本机验收核验：该请求在传输前被工作流预算定义缺失拒绝，持久化 MCP 失败明确 dispatched=false；模拟生产配置仍 fixture-1/FAULT 且原子部署回执为 0。仅解除未执行记录的不确定性，不宣称操作成功。',
    }, h['token'])
    output.with_suffix('.result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    assert result['remainingUnresolved'] == 0
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    reconcile(parser.parse_args().output)
