#!/usr/bin/env python3
"""Read-only cross-check of an existing browser-triggered C run, preserving SQL and raw evidence.

Execution completion and business PASS/FAIL/INCONCLUSIVE are reported separately.
Never writes approvals, target state, metric samples or expected verdicts.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import runpy
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def inspect(run, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new output path to preserve previous observations')
    if not re.fullmatch(r'(chat-chat-session-[a-z0-9-]+|auto-c-[a-f0-9]{64})', run):
        raise ValueError('Supply an existing manual or automatically triggered C Run ID')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    statements = []
    report = {'runId': run, 'crossChecks': 'NOT_COMPLETED'}

    def rows(query):
        statements.append(query + ';')
        return h['rows'](query)

    q = h['quoted']
    try:
        fact = rows("SELECT JSON_OBJECT('status',status,'agentId',agent_id,'version',agent_version,"
                    "'hash',agent_definition_hash,'request',CAST(request_json AS JSON)) FROM ai_ops_agent_run WHERE run_id=" + q(run))[0]
        report['run'] = fact
        if run.startswith('auto-c-'):
            report['automaticJobs'] = rows("SELECT JSON_OBJECT('eventKey',event_key,'owner',owner,"
                "'runId',run_id,'sessionId',session_id,'status',status,'failures',failures,"
                "'workflowVersion',workflow_version,'workflowHash',workflow_hash,'createdAt',created_at,"
                "'updatedAt',updated_at) FROM ai_ops_change_verification_job WHERE run_id=" + q(run))
            assert len(report['automaticJobs']) == 1
            job = report['automaticJobs'][0]
            assert job['owner'] == fact['request']['userId']
            assert job['workflowHash'] == fact['hash'] and job['workflowVersion'] == fact['version']
            assert fact['request']['trustedObserveOnly'] is True
        assert fact['status'] in ('SUCCEEDED', 'FAILED', 'CANCELED'), 'Run has not finished'
        checkpoint = rows("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=" + q(run)
                          + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")[0]
        outputs = {k.split(':')[1]: v for k, v in checkpoint['state']['variables'].items()
                   if k.startswith('nodeOutput:') and k.count(':') == 1}
        business = next((outputs[n]['workflowData_report'] for n in ('review-extended', 'unavailable', 'review')
                         if 'workflowData_report' in outputs.get(n, {})), {})
        report.update(executedNodes=list(outputs), businessVerdict=business.get('status', 'NOT_REACHED'), businessReport=business)
        package = business.get('changeRef', {}).get('packageId')
        if package:
            report['package'] = rows("SELECT JSON_OBJECT('id',package_id,'project',project_id,'status',status,"
                 "'version',approved_version,'hash',approved_package_hash,'landingRun',landing_run_id) "
                 "FROM ai_ops_change_package WHERE package_id=" + q(package))[0]
            report['operationTimes'] = rows("SELECT JSON_OBJECT('operation',o.operation_id,'landingRun',o.landing_run_id,"
                 "'journalStart',o.started_at,'ledgerStart',e.created_at,'journalFinish',o.finished_at,"
                 "'receipt',o.result_id,'hash',o.output_hash,'ledgerReceipt',e.result_id,'ledgerHash',e.output_hash) "
                 "FROM ai_ops_change_package_landing_operation_run o LEFT JOIN ai_ops_tool_execution_ledger e "
                 "ON o.execution_key=e.idempotency_key AND o.project_id=e.project_id AND o.landing_run_id=e.run_id "
                 "WHERE o.package_id=" + q(package))
        reservations = rows("SELECT JSON_OBJECT('id',request_id,'node',node_id,'tool',tool_name,'budget',budget_limit) "
                            "FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + q(run) + " ORDER BY id")
        request = urllib.request.Request('http://127.0.0.1:18862/evidence', headers={
            'Authorization': 'Bearer ' + h['values']['OPS_ACCEPTANCE_OBSERVABILITY_TOKEN']})
        with urllib.request.urlopen(request, timeout=15) as response:
            peer = json.load(response)
        ids = {r['id'] for r in reservations}
        calls = [c for c in peer['rpcCalls'] if c['rpc_id'] in ids]
        queries = [c for c in peer['queries'] if c['rpc_id'] in ids]
        assert len(calls) == len(reservations) <= 12
        assert {c['rpc_id'] for c in calls} == ids
        assert all(c['budget'] == 12 for c in reservations)
        report.update(dispatches=reservations, remoteCalls=calls, upstreamQueries=queries)
        receipts = rows("SELECT JSON_OBJECT('id',result_id,'source',source,'hash',output_hash,'output',full_output) "
                        "FROM ai_ops_tool_result WHERE run_id=" + q(run) + " AND source='MCP_REMOTE_TOOL'")
        for receipt in receipts:
            assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
        for value in outputs.values():
            for key, envelope in value.items():
                if key.startswith('workflowData_') and isinstance(envelope, dict) and envelope.get('providerResultId'):
                    matches = [r for r in receipts if r['id'] == envelope['providerResultId']]
                    assert len(matches) == 1 and matches[0]['hash'] == envelope['providerOutputHash']
                    assert json.loads(matches[0]['output'])['normalizedContent'] == envelope['normalizedContent']
        report['receipts'] = receipts
        windows = {}
        for phase in ('before', 'after'):
            window = business.get(phase, {}).get('window')
            if not window:
                continue
            start, end = [dt.datetime.fromtimestamp(window[k], dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')
                          for k in ('startEpoch', 'endEpoch')]
            windows[phase] = rows("SELECT JSON_OBJECT('count',COUNT(*),'uniqueRequests',COUNT(DISTINCT event_id),"
                 "'errors',COALESCE(SUM(http_status>=500),0),'versions',GROUP_CONCAT(DISTINCT version)) "
                 "FROM ops_acceptance_business_a.ops04_request WHERE service_id=" + q(window['serviceId'])
                 + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)>=" + q(start)
                 + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)<" + q(end))[0]
            assert windows[phase]['count'] == windows[phase]['uniqueRequests']
        report['independentCompletedRequestWindows'] = windows
        with urllib.request.urlopen('http://127.0.0.1:18262/version', timeout=10) as response:
            report['actualTarget'] = json.load(response)
        if fact['status'] == 'SUCCEEDED':
            assert 'end' in outputs and business, 'No committed terminal business report'
        report['crossChecks'] = 'PASS'
    except Exception as error:
        report.update(crossChecks='FAIL', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text('\n'.join(statements) + '\n')
    print(json.dumps({'crossChecks': report['crossChecks'], 'runStatus': fact['status'],
                      'businessVerdict': report['businessVerdict'], 'physicalCalls': len(calls)}, ensure_ascii=False))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run-id', required=True)
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    inspect(args.run_id, args.output)
