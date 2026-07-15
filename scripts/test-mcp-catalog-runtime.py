#!/usr/bin/env python3
"""Verify deployed durable catalog reuse using real SDK definitions and the observability peer's business-call ledger.

Schema hydration changes only the platform's ordinary metadata snapshots. No tool is executed.
"""
import argparse
import json
from pathlib import Path
import runpy
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main(output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new output path; earlier evidence is retained')
    r = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    project = 'ops-acceptance-a'
    tool = next(t for t in r['api']('/api/v1/admin/ops/projects/' + project + '/tools', token=r['token'])
                if t['mcpName'] == 'OPS-04 real observability' and t['status'] == 'ENABLED')
    mcp = tool['mcpId']
    query = "SELECT JSON_OBJECT('generation',generation,'hash',content_hash,'toolCount',JSON_LENGTH(tools_json),'checkedAt',checked_at,'succeededAt',succeeded_at,'error',error_code) FROM ai_ops_mcp_remote_catalog WHERE project_id=" + r['quoted'](project) + ' AND server_id=' + r['quoted'](mcp)
    def counts():
        request = urllib.request.Request('http://127.0.0.1:18862/evidence', headers={
            'Authorization': 'Bearer ' + r['values']['OPS_ACCEPTANCE_OBSERVABILITY_TOKEN']})
        with urllib.request.urlopen(request, timeout=10) as response:
            evidence = json.load(response)
        return {'businessCalls': len(evidence['rpcCalls']), 'queries': len(evidence['queries'])}
    proof = {'status': 'RUNNING', 'scope': 'REAL_SDK_REAL_MCP_REAL_MYSQL_REAL_SQLITE', 'projectId': project,
             'mcpId': mcp, 'before': counts()}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query + ';\n')
    try:
        path = '/api/v1/admin/ops/projects/' + project + '/tool-router/hydrate-schema'
        payload = {'toolId': mcp, 'remoteToolName': 'metrics_window'}
        first = r['api'](path, 'POST', payload, r['token'])
        proof['afterFirst'] = counts()
        proof['catalogFirst'] = r['rows'](query)
        second = r['api'](path, 'POST', payload, r['token'])
        proof['afterSecond'] = counts()
        proof['catalogSecond'] = r['rows'](query)
        status_path = '/api/v1/admin/ops/projects/' + project + '/mcp-remote-catalogs'
        statuses = r['api'](status_path, token=r['token'])
        matching = [s for s in statuses if s['serverId'] == mcp]
        assert matching and all(s['toolCount'] == 5 for s in matching)
        assert all(set(s) == {'connectionId', 'serverId', 'generation', 'toolCount',
                              'checkedAt', 'succeededAt', 'errorCode'} for s in statuses)
        credentials = json.loads((ROOT / 'deploy/.acceptance-private/users.json').read_text())
        proof['permissions'] = {'admin': 'PASS', 'statusMetadataOnly': True}
        for username in ('ops_acceptance_viewer', 'ops_acceptance_b_member'):
            user_token = r['api_module']['login'](credentials[username])
            proof['permissions'][username] = r['api'](status_path, token=user_token, denied=True)
            assert proof['permissions'][username]['httpStatus'] == 403
        assert proof['catalogFirst'] and all(x['generation'] >= 1 and x['toolCount'] == 5 for x in proof['catalogFirst'])
        assert proof['catalogSecond'] == proof['catalogFirst']
        assert proof['afterSecond'] == proof['before'], 'Discovery must not execute a business tool'
        proof['boundary'] = 'This probe verifies persisted catalog reuse and no business dispatch; tools/list wire counts are checked by the real peer integration test.'
        proof['hydrationReturnedDefinitions'] = bool(first and second)
        proof['status'] = 'PASS'
    except Exception as error:
        proof['status'] = 'FAIL'
        proof['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(proof, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args().output)
