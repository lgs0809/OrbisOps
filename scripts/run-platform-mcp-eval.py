#!/usr/bin/env python3
"""Run frozen synthetic component cases through the deployed Workflow and actual HTTP peer.

Normal user input is natural language. Published DIRECT actions bind the frozen
case and a fresh attempt identity internally. This measures protocol behavior,
not model quality or production incidents. Negative cases require a real
read-snapshot node and physical dispatch; a planner failure never passes.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import secrets
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT/'deploy/.acceptance-private/platform-mcp-v3'
spec = importlib.util.spec_from_file_location('platform_eval_common', ROOT/'scripts/run-platform-inspection-eval.py')
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)
common.DATASET = 'mcp'
common.CORPUS = CORPUS
API, TOKEN, SQL, Q, ROWS = common.API, common.TOKEN, common.sql, common.q, common.rows


def peer_evidence(attempt=None):
    endpoint = 'http://127.0.0.1:8781/evidence'+('?attemptId='+attempt if attempt else '')
    code = ('import os,urllib.request; r=urllib.request.Request("'+endpoint+'",'
        'headers={"Authorization":"Bearer "+os.environ["MCP_ACCEPTANCE_TOKEN"]});'
        'print(urllib.request.urlopen(r,timeout=5).read().decode())')
    return json.loads(subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-platform-contract-mcp-1',
        'python', '-c', code], text=True))


def mount_isolation():
    value = json.loads(subprocess.check_output(['docker', 'inspect', 'orbisops-acceptance-platform-contract-mcp-1'], text=True))[0]
    mounts = [{k: m.get(k) for k in ['Type', 'Source', 'Destination', 'RW', 'Name']} for m in value['Mounts']]
    bind = {m['Destination']: m for m in mounts if m['Type'] == 'bind'}
    source = bind.get('/eval/facts.json', {}).get('Source', '')
    expected_source = str(CORPUS/'facts.json')
    if source == '/host_mnt'+expected_source:
        source = expected_source
    mounted_hash = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-platform-contract-mcp-1',
        'python', '-c', 'import hashlib;print(hashlib.sha256(open("/eval/facts.json","rb").read()).hexdigest())'],
        text=True).strip()
    manifest = common.verify_corpus()
    checks = {'factsOnly': set(bind) == {'/eval/facts.json', '/eval/server.py'},
        'factsCorrect': source == expected_source and mounted_hash == manifest['factsSha256'],
        'bindReadOnly': all(m['RW'] is False for m in bind.values()),
        'dedicatedLedger': any(m['Destination'] == '/state' and m['Type'] == 'volume'
            and m['Name'] == 'orbisops-acceptance_platform-contract-data' for m in mounts),
        'readOnlyRoot': value['HostConfig']['ReadonlyRootfs'] is True,
        'noHostPorts': not value['HostConfig'].get('PortBindings')}
    if not all(checks.values()): raise RuntimeError('Peer isolation failed: '+json.dumps(checks))
    return {'checks': checks, 'mounts': mounts, 'mountedFactsSha256': mounted_hash, 'peerSourceSha256': hashlib.sha256(
        (ROOT/'scripts/fixtures/platform-mcp-contract-peer.py').read_bytes()).hexdigest()}


def prepare(output):
    manifest = common.verify_corpus()
    mount = mount_isolation()
    actor = next(u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN)
        if u['username'] == common.CREDENTIALS['username'])
    projects = {p['projectId'] for p in API('/api/v1/admin/ops-projects/snapshot', token=TOKEN)['projects']}
    users = {u['username']: u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN)}
    private = ROOT/'deploy/.acceptance-private/platform-mcp-users.json'
    if private.exists(): credentials = json.loads(private.read_text())
    else:
        if any('ops_eval_mcp_'+s in users for s in ['dev', 'holdout']):
            raise RuntimeError('Evaluator already exists without retained credentials; preserved')
        credentials = {s: {'username': 'ops_eval_mcp_'+s, 'password': secrets.token_urlsafe(36)} for s in ['dev', 'holdout']}
        private.write_text(json.dumps(credentials)+'\n'); private.chmod(0o600)
    prepared = {'scope': manifest['scope'], 'manifest': manifest, 'integrations': {}, 'isolation': mount,
        'preparedAt': dt.datetime.now(dt.timezone.utc).isoformat()}
    for split in ['dev', 'holdout']:
        user = credentials[split]
        if user['username'] not in users:
            API('/api/v1/admin/admin-user/create', 'POST', {**user, 'userId': user['username'], 'userRole': 'user', 'status': 1}, TOKEN)
        project = 'ops-platform-mcp-'+split
        if project not in projects:
            API('/api/v1/admin/ops-projects/projects', 'POST', {'projectId': project, 'name': '冻结MCP组件 · '+split,
                'owner': common.CREDENTIALS['username'], 'environments': ['sandbox'],
                'description': 'SYNTHETIC_COMPONENT_EVAL_ONLY；不进入任务验收、Skill或长期记忆。'}, TOKEN)
        member_path = '/api/v1/admin/ops-projects/projects/'+project+'/members'
        members = common.SEED['normalized_members'](API(member_path, token=TOKEN))
        if not any(m['userId'] == user['username'] for m in members):
            API(member_path, 'PUT', {'members': members+[{'userId': user['username'], 'username': user['username'], 'memberRole': 'MEMBER'}]}, TOKEN)
        common.isolation(project)
        path = '/api/v1/admin/ops/projects/'+project
        name = 'OPS frozen MCP contract '+split
        integrations = [m for m in API(path+'/tools', token=TOKEN) if m['mcpName'] == name]
        if not integrations or (len(integrations) == 1 and integrations[0]['status'] == 'PENDING_REVIEW'
                and not integrations[0]['remoteTools']):
            API('/api/v1/admin/ops/tool-executions', 'POST', {'projectId': project, 'toolsetId': 'capability.manage',
                'toolName': 'mcp_server_import', 'userId': actor['userId'], 'authenticatedUsername': common.CREDENTIALS['username'],
                'executionScope': 'PRE_APPROVAL_WORKFLOW', 'runId': 'platform-mcp-import-'+uuid.uuid4().hex,
                'arguments': {'sourceUrl': 'http://127.0.0.1:8781/mcp', 'capabilityName': name,
                    'credentialRef': '${env:OPS_ACCEPTANCE_MCP_TOKEN}', 'transportType': 'streamable-http'}}, TOKEN)
            integrations = [m for m in API(path+'/tools', token=TOKEN) if m['mcpName'] == name]
        if len(integrations) != 1: raise RuntimeError('Ambiguous integration; preserved')
        mcp = integrations[0]
        if mcp['transportConfig']['endpoint'] != 'http://127.0.0.1:8781/mcp' or mcp['status'] not in ['ENABLED', 'PENDING_REVIEW']:
            raise RuntimeError('Changed/disabled integration; preserved')
        if [t['toolName'] for t in mcp['remoteTools']] != ['read_snapshot']: raise RuntimeError('Unexpected remote tools')
        policy = next(p for p in API(path+'/mcp-tool-policies', token=TOKEN)
            if p['mcpId'] == mcp['mcpId'] and p['toolName'] == 'read_snapshot')
        if policy['status'] == 'PENDING_REVIEW':
            policy = API(path+'/mcp-tool-policies/'+policy['policyId']+'/approve', 'POST', {
                'effectType': 'READ_EXTERNAL_STATE', 'effectScope': 'TARGET_RESOURCE_READ', 'mutability': 'READ_ONLY',
                'capability': 'READ_ONLY', 'allowedActions': ['READ_SNAPSHOT'], 'riskLevel': 'LOW', 'readOnly': True,
                'investigateAllowed': True, 'prepareAllowed': False, 'landAllowed': False,
                'requiresApprovedPackage': False, 'requiresHumanApproval': False, 'requiresDryRun': False,
                'requiresRollbackPlan': False, 'disclosureTier': 'CORE',
                'reason': '已授权本机合成组件评测：冻结facts只读，reference不挂载，无业务写入，仅独立调用账本。'}, TOKEN)
        if policy['status'] != 'ACTIVE' or policy.get('readOnly') is not True or policy.get('landAllowed') is not False:
            raise RuntimeError('Changed/unapproved policy; preserved')
        if mcp['status'] != 'ENABLED': API(path+'/tools/'+mcp['mcpId']+'/status', 'PATCH', {'status': 'ENABLED'}, TOKEN)
        prepared['integrations'][project] = {'mcpId': mcp['mcpId'], 'policyId': policy['policyId'], 'evaluatorUserId': user['username']}
    if peer_evidence()['sourceSha256'] != manifest['factsSha256']: raise RuntimeError('Peer facts changed')
    output.write_text(json.dumps(prepared, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': 'READY', 'integrations': prepared['integrations']}, ensure_ascii=False))


def definition(case, mcp, attempt):
    value = json.loads((ROOT/'scripts/fixtures/workflow-mcp-read.json').read_text())
    value.update(agentId='ops-mcp-eval-'+attempt, projectId=case['projectId'], name='冻结只读回执 · '+case['caseId'],
        description='合成组件评测：实际HTTP与平台Workflow，无模型性能或生产事故声明。')
    value['nodes'][0]['config']['maxRealToolCalls'] = 2
    node = value['nodes'][1]
    node.update(nodeId='read-snapshot', mcpIds=[mcp], outputKey='receipt')
    # The remote schema remains the strict output contract. Graph state carries
    # one canonical observation and immutable DB references instead of repeating
    # the entire raw envelope as the bounded node's user-facing output.
    node['config'] = {'inheritProjectCapabilities': False, 'actions': [{'mcpId': mcp, 'remoteToolName': 'read_snapshot',
        'arguments': {'caseId': case['caseId'], 'projectId': case['projectId'], 'attemptId': attempt},
        'structuredOutputKey': 'snapshot', 'outputMode': 'MCP_EVIDENCE_REFERENCE'}]}
    value['nodes'][-1]['config']['outputKeys'] = ['receipt']
    value['edges'] = [{'from': a, 'to': b, 'conditionType': 'always', 'condition': 'always'}
        for a, b in [('start', 'read-snapshot'), ('read-snapshot', 'end')]]
    return value


def fresh_integration(project, attempt):
    """Use a new authority/circuit key, never clear existing platform state."""
    actor = next(u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN)
        if u['username'] == common.CREDENTIALS['username'])
    name = 'OPS MCP independent attempt '+attempt
    path = '/api/v1/admin/ops/projects/'+project
    imported = API('/api/v1/admin/ops/tool-executions', 'POST', {'projectId': project, 'toolsetId': 'capability.manage',
        'toolName': 'mcp_server_import', 'userId': actor['userId'], 'authenticatedUsername': common.CREDENTIALS['username'],
        'executionScope': 'PRE_APPROVAL_WORKFLOW', 'runId': 'platform-mcp-import-'+attempt,
        'arguments': {'sourceUrl': 'http://127.0.0.1:8781/mcp', 'capabilityName': name,
            'credentialRef': '${env:OPS_ACCEPTANCE_MCP_TOKEN}', 'transportType': 'streamable-http'}}, TOKEN)
    # The normal import response contains its persisted, project-bound MCP and
    # actual discovered schemas. Avoid refetching thousands of unrelated tools
    # just to find the single integration created by this request.
    mcp = imported.get('mcp')
    if imported.get('status') != 'DISCOVERED_PENDING_REVIEW' or not isinstance(mcp, dict) or \
            mcp.get('projectId') != project or mcp.get('mcpName') != name or not mcp.get('mcpId') or \
            mcp.get('transportConfig', {}).get('endpoint') != 'http://127.0.0.1:8781/mcp':
        raise RuntimeError('Fresh attempt authoritative import failed; no case counted')
    if [t['toolName'] for t in mcp['remoteTools']] != ['read_snapshot']: raise RuntimeError('Remote schema discovery failed')
    suggestions = imported.get('policySuggestions', [])
    if len(suggestions) != 1 or suggestions[0].get('toolName') != 'read_snapshot' or not suggestions[0].get('policyId'):
        raise RuntimeError('Fresh attempt authoritative policy suggestion missing; no case counted')
    policy = suggestions[0]
    policy = API(path+'/mcp-tool-policies/'+policy['policyId']+'/approve', 'POST', {
        'effectType': 'READ_EXTERNAL_STATE', 'effectScope': 'TARGET_RESOURCE_READ', 'mutability': 'READ_ONLY',
        'capability': 'READ_ONLY', 'allowedActions': ['READ_SNAPSHOT'], 'riskLevel': 'LOW', 'readOnly': True,
        'investigateAllowed': True, 'prepareAllowed': False, 'landAllowed': False,
        'requiresApprovedPackage': False, 'requiresHumanApproval': False, 'requiresDryRun': False,
        'requiresRollbackPlan': False, 'disclosureTier': 'CORE',
        'reason': '已授权合成评测独立尝试；相同只读facts与严格schema，新authority仅隔离熔断初态，不重置已有集成。'}, TOKEN)
    if policy['status'] != 'ACTIVE' or policy.get('readOnly') is not True: raise RuntimeError('Read policy activation failed')
    API(path+'/tools/'+mcp['mcpId']+'/status', 'PATCH', {'status': 'ENABLED'}, TOKEN)
    return {'mcpId': mcp['mcpId'], 'policyId': policy['policyId'],
        'importReceipt': {k: imported.get(k) for k in ['resultId', 'outputHash', 'inputHash']},
        'remoteSchemaSha256': hashlib.sha256(common.canonical(mcp['remoteTools']).encode()).hexdigest()}


def validation_binding(test_summary, deployment, reused_result, artifacts, manifest):
    """Bind actual execution to independently checked tests and retained seen cases."""
    if not test_summary or not deployment:
        raise RuntimeError('Formal execution requires actual test and deployment evidence')
    tested = json.loads(test_summary.read_text())
    deployed = json.loads(deployment.read_text())
    jar = artifacts['deployedJarSha256']
    if tested.get('status') != 'PASS' or deployed.get('status') != 'PASS' or \
            not all(tested.get('checks', {}).values()) or not all(deployed.get('checks', {}).values()) or \
            tested.get('jarSha256') != jar or deployed.get('jarSha256') != jar or \
            tested.get('sourceHash') != deployed.get('sourceHash') or \
            tested.get('totals', {}).get('failures') != 0 or tested.get('totals', {}).get('errors') != 0:
        raise RuntimeError('Tested source, deployment and actual JAR do not agree')
    value = {'testedProductionSourceSha256': tested['sourceHash'], 'deployedJarSha256': jar,
        'testSummary': {'path': str(test_summary), 'sha256': hashlib.sha256(test_summary.read_bytes()).hexdigest(),
            'totals': tested['totals']},
        'deployment': {'path': str(deployment), 'sha256': hashlib.sha256(deployment.read_bytes()).hexdigest()},
        'holdoutPreviouslySeen': bool(reused_result), 'newIndependentCases': 0 if reused_result else None}
    if reused_result:
        prior = json.loads(reused_result.read_text())
        if any(prior.get('manifest', {}).get(key) != manifest[key] for key in ['factsSha256', 'referenceSha256']):
            raise RuntimeError('Reuse disclosure must retain the same frozen facts and reference')
        value['reusedResults'] = {'path': str(reused_result),
            'sha256': hashlib.sha256(reused_result.read_bytes()).hexdigest(),
            'originalDenominators': prior.get('denominators'),
            'boundary': 'REGRESSION_ON_PREVIOUSLY_SEEN_FROZEN_CASES_NOT_NEW_HOLDOUT'}
    return value


def run(prepared_file, output, split, repeats, limit, review, test_summary=None, deployment=None, reused_result=None):
    manifest = common.verify_corpus()
    prepared = json.loads(prepared_file.read_text())
    if prepared['manifest'] != manifest: raise RuntimeError('Prepared corpus differs')
    public = {c['caseId']: c for c in json.loads((CORPUS/'facts.json').read_text())}
    references = [c for c in json.loads((CORPUS/'reference.json').read_text()) if split == 'all' or c['split'] == split]
    attempts = [(c, n) for c in references for n in range(1, repeats+1)]
    if limit: attempts = attempts[:limit]
    credentials = json.loads((ROOT/'deploy/.acceptance-private/platform-mcp-users.json').read_text())
    tokens = {s: common.SEED['login'](v) for s, v in credentials.items()}
    report = {'scope': manifest['scope'], 'status': 'RUNNING', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'stage': 'FORMAL_FIVE_REPEAT' if repeats == 5 and not limit else 'DEVELOPMENT_SCREENING',
        'repeats': repeats, 'split': split, 'models': [], 'modelQualityClaim': False, 'results': [],
        'artifacts': common.runtime_identity(), 'peerIsolation': mount_isolation(), 'manifest': manifest,
        'preparedSha256': hashlib.sha256(prepared_file.read_bytes()).hexdigest()}
    checked = json.loads(review.read_text())
    if checked.get('factsSha256') != manifest['factsSha256'] or checked.get('referenceSha256') != manifest['referenceSha256']:
        raise RuntimeError('Independent review does not match the frozen corpus')
    report['independentReview'] = {'path': str(review), 'sha256': hashlib.sha256(review.read_bytes()).hexdigest()}
    if repeats == 5 and not limit:
        report['validationBinding'] = validation_binding(test_summary, deployment, reused_result, report['artifacts'], manifest)
    try:
        for reference, repetition in attempts:
            case = public[reference['caseId']]; project = case['projectId']; attempt = uuid.uuid4().hex
            before = common.isolation(project)
            integration = fresh_integration(project, attempt)
            config = definition(case, integration['mcpId'], attempt)
            saved = API('/api/v1/admin/ops-agents/drafts', 'POST', config, TOKEN)
            path = '/api/v1/admin/ops-agents/'+saved['agentId']+'/versions/'+str(saved['version'])
            for action in ['validate', 'publish']: saved = API(path+'/'+action, 'POST', {}, TOKEN)
            token = tokens[project.rsplit('-', 1)[-1]]
            session = API('/api/v1/user/chat/session', 'POST', {'projectId': project, 'agentId': saved['agentId'],
                'agentVersion': saved['version'], 'title': '合成MCP组件 · '+case['caseId']+' · '+str(repetition)}, token)
            started = time.monotonic()
            response = API('/api/v1/user/chat/sessions/'+session+'/messages', 'POST', {'projectId': project,
                'query': case['query'], 'mode': 'AGENT', 'engine': 'GRAPH', 'agentDefinitionId': saved['agentId'],
                'agentVersion': saved['version'], 'metadata': {'executionType': 'WORKFLOW', 'fixture': 'SYNTHETIC_MCP_COMPONENT_NO_LEARNING'}}, token, timeout=240)
            run_id = response['metadata']['runId']
            deadline = time.monotonic()+240
            while True:
                state = ROWS("SELECT JSON_OBJECT('status',status,'error',error_message,'hash',agent_definition_hash,'userId',user_id) FROM ai_ops_agent_run WHERE run_id="+Q(run_id))[0]
                if state['status'] in ['SUCCEEDED', 'FAILED', 'CANCELED']: break
                if time.monotonic()>deadline: raise TimeoutError('Actual run still pending: '+run_id)
                time.sleep(1)
            events = API('/api/v1/admin/ops-agent-runs/'+run_id+'/events/list', token=TOKEN)
            checkpoint = ROWS('SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id='+Q(run_id)+' ORDER BY checkpoint_seq')
            dispatches = ROWS("SELECT JSON_OBJECT('rpcId',request_id,'attempt',physical_attempt,'limit',budget_limit) FROM ai_ops_workflow_tool_dispatch WHERE run_id="+Q(run_id)+' ORDER BY id')
            calls = ROWS("SELECT JSON_OBJECT('tool',tool_name,'readOnly',read_only,'status',status,'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id="+Q(run_id)+' ORDER BY id')
            receipts = ROWS("SELECT JSON_OBJECT('hash',output_hash,'output',full_output,'status',status) FROM ai_ops_tool_result WHERE run_id="+Q(run_id)+" AND source='MCP_REMOTE_TOOL' ORDER BY id")
            peer = peer_evidence(attempt); physical = [p for p in peer['calls'] if p['attempt_id'] == attempt]
            redirects = [p for p in peer['redirects'] if p['attempt_id'] == attempt]
            denied = [p for p in peer['deniedReads'] if p['attempt_id'] == attempt]
            normalized = [json.loads(r['output']).get('normalizedContent') for r in receipts if r['status'] == 'SUCCEEDED']
            checks = {'expectedTerminal': state['status'] == reference['expectedRunStatus'],
                'frozenPublishedDefinition': state['hash'] == saved['definitionHash'],
                'creatorIdentity': state['userId'] == prepared['integrations'][project]['evaluatorUserId'],
                'actualSpecifiedNode': any(e.get('nodeId') == 'read-snapshot' for e in events),
                'physicalCallRequired': reference['minPhysicalToolCalls'] <= len(physical) <= reference['maxPhysicalToolCalls'],
                'exactCallsWhenSpecified': reference.get('expectedPhysicalToolCalls') is None or len(physical) == reference['expectedPhysicalToolCalls'],
                'physicalLedgerMatches': bool(physical) and len(dispatches) == len(physical)
                    and sorted(d['rpcId'] for d in dispatches) == sorted(p['rpc_id'] for p in physical),
                'allReadOnly': bool(calls) and all(c['tool'] == 'read_snapshot' and c['readOnly'] == 1 for c in calls),
                'firstAuthoritativeReceipt': (bool(normalized) and all(n == reference['expectedNormalizedContent'] for n in normalized))
                    if reference['expectedRunStatus'] == 'SUCCEEDED' else not normalized,
                'receiptHashes': all(hashlib.sha256(r['output'].encode()).hexdigest() == r['hash'] for r in receipts),
                'factsHash': peer['sourceSha256'] == manifest['factsSha256'] and all(p['source_sha256'] == manifest['factsSha256'] for p in physical),
                'noForeignReads': not denied and all(p['resource_project_id'] == project for p in physical),
                'noRedirectAuthority': len(redirects) == reference['expectedUnauthorizedRedirectRequests'],
                'noBusinessWrites': peer['businessWrites'] == reference['expectedBusinessWrites'],
                'noModelRequests': not any(e.get('eventType') == 'MODEL_REQUEST_ATTEMPT' for e in events),
                'learningIsolation': before == common.isolation(project),
                'noInjectedMemory': not any(e.get('eventType') == 'MEMORY_CONTEXT_FINISHED' and
                    e.get('payload', {}).get('memoryContextChars', 0) for e in events)}
            result = {'caseId': case['caseId'], 'family': reference['family'], 'split': reference['split'], 'repetition': repetition,
                'status': 'PASS' if all(checks.values()) else 'FAIL', 'checks': checks, 'runId': run_id,
                'attemptId': attempt, 'state': state, 'durationSeconds': round(time.monotonic()-started, 3),
                'definitionHash': saved['definitionHash'], 'definitionTemplateHash': hashlib.sha256(common.canonical(config).encode()).hexdigest(),
                'isolatedAttemptIntegration': integration,
                'peerCalls': physical, 'unauthorizedRedirects': redirects, 'deniedReads': denied,
                'dispatches': dispatches, 'businessCalls': calls, 'receipts': receipts, 'checkpoints': checkpoint}
            report['results'].append(result)
            output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            output.with_name(output.stem+'-'+case['caseId']+'-'+str(repetition)+'-events.json').write_text(json.dumps(events, ensure_ascii=False, indent=2)+'\n')
            print(json.dumps({k: result[k] for k in ['caseId', 'repetition', 'status', 'checks', 'runId', 'durationSeconds']}, ensure_ascii=False), flush=True)
        report['status'] = 'PASS' if all(r['status'] == 'PASS' for r in report['results']) else 'FAIL'
    except Exception as error:
        report['status'] = 'BLOCKED'; report['error'] = type(error).__name__+': '+str(error)
        raise
    finally:
        report['finalRuntimeIdentity'] = common.runtime_identity()
        report['artifactUnchangedDuringRun'] = report['finalRuntimeIdentity']['deployedJarSha256'] == report['artifacts']['deployedJarSha256']
        if not report['artifactUnchangedDuringRun']:
            report['status'] = 'INVALID_ARTIFACT_CHANGED'
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        report['denominators'] = {'availableUniqueCases': len(references),
            'plannedUniqueCases': len({reference['caseId'] for reference, repetition in attempts}), 'plannedAttempts': len(attempts),
            'completedUniqueCases': len({r['caseId'] for r in report['results']}), 'completedAttempts': len(report['results']),
            'passedAttempts': sum(r['status'] == 'PASS' for r in report['results'])}
        report['caseReliability'] = []
        for reference in references:
            actual = [r for r in report['results'] if r['caseId'] == reference['caseId']]
            n, p = len(actual), sum(r['status'] == 'PASS' for r in actual)
            report['caseReliability'].append({'caseId': reference['caseId'], 'attempts': n, 'passed': p,
                **{f'pass^{k}': (math.comb(p, k)/math.comb(n, k) if p >= k else 0) if n >= k else None for k in [1, 3, 5]}})
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        output.with_suffix('.sql').write_text('\n'.join(common.STATEMENTS)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--prepared', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--split', choices=['development', 'holdout', 'all'], default='development')
    parser.add_argument('--repeats', type=int, choices=[1, 5], default=1)
    parser.add_argument('--limit', type=int)
    parser.add_argument('--review', type=Path, help='Independent corpus review bound to these exact facts/reference hashes')
    parser.add_argument('--test-summary', type=Path, help='Fresh independently parsed complete test evidence for the deployed JAR')
    parser.add_argument('--deployment', type=Path, help='Verified deployment evidence bound to the tested source/JAR')
    parser.add_argument('--reuse-results', type=Path, help='Retained results from these previously seen frozen cases; never a new holdout claim')
    args = parser.parse_args()
    if args.output.exists(): parser.error('Use a fresh evidence path; previous results are retained')
    if args.repeats == 1 and args.split != 'development': parser.error('Holdout is evaluated only in the declared five-repeat formal stage')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.prepare: prepare(args.output)
    elif args.prepared and args.review: run(args.prepared, args.output, args.split, args.repeats, args.limit, args.review,
        args.test_summary, args.deployment, args.reuse_results)
    else: parser.error('Use --prepare or --prepared')
