#!/usr/bin/env python3
"""Read-only audit of the actual browser Skill revocation workflow.

Verify remote receipts against the provider ledger and actual target version.
Writes inspection SQL, raw result and evidence; never changes runtime state.
"""
from pathlib import Path
import argparse, hashlib, json, re, runpy, urllib.request
ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-acceptance-a'
SKILL='skill-ops06-related-project-fixture'

def main(run,output,expected_status,expected_calls,workflow="ops-acceptance-skill-revocation"):
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+',run):
        raise ValueError('Use the actual browser run ID')
    s=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    q=s['quoted']; statements=[]
    def rows(statement):
        statements.append(statement+';')
        return s['rows'](statement)
    fact=rows("SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'agentId',agent_id,'status',status,'version',agent_version,'hash',agent_definition_hash,'epoch',fencing_token,'error',error_message) FROM ai_ops_agent_run WHERE run_id="+q(run))[0]
    assert fact['projectId']==PROJECT and fact['agentId']==workflow,fact
    bundles=rows("SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'bundleHash',bundle_hash,'refs',used_skill_version_refs_json,'refsHash',used_skill_refs_hash,'bundle',bundle_json) FROM ai_ops_runtime_context_bundle WHERE run_id="+q(run))
    skills=rows("SELECT JSON_OBJECT('skillId',skill_id,'scope',scope,'projectId',project_id,'status',status,'version',current_version,'hash',current_skill_hash,'packageHash',current_package_hash) FROM ai_ops_skill WHERE project_id="+q(PROJECT)+" AND skill_id="+q(SKILL))
    approvals=rows("SELECT JSON_OBJECT('id',approval_id,'status',status,'node',node_id) FROM ai_ops_workflow_approval WHERE run_id="+q(run))
    receipts=rows("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'output',full_output,'status',status) FROM ai_ops_tool_result WHERE run_id="+q(run)+" AND project_id="+q(PROJECT)+" AND source='MCP_REMOTE_TOOL' ORDER BY id")
    dispatches=rows("SELECT JSON_OBJECT('rpcId',request_id,'nodeId',node_id,'tool',tool_name,'attempt',physical_attempt,'limit',budget_limit) FROM ai_ops_workflow_tool_dispatch WHERE run_id="+q(run)+" ORDER BY id")
    req=urllib.request.Request('http://127.0.0.1:18862/evidence',headers={'Authorization':'Bearer '+s['values']['OPS_ACCEPTANCE_OBSERVABILITY_TOKEN']})
    with urllib.request.urlopen(req,timeout=10) as response: peer=json.load(response)
    with urllib.request.urlopen('http://127.0.0.1:18262/version',timeout=10) as response: target=json.load(response)
    rpc={d['rpcId'] for d in dispatches}
    calls=[c for c in peer['rpcCalls'] if c['rpc_id'] in rpc]
    queries=[c for c in peer['queries'] if c['rpc_id'] in rpc]
    detail=s['api']('/api/v1/admin/ops/analysis-tasks/'+run+'?projectId='+PROJECT,token=s['token'])
    presented={k:detail[k] for k in ['runId','projectId','status','errorMessage','technicalError','summary']}
    evidence={'status':'UNVERIFIED','run':fact,'presentation':presented,'bundles':bundles,'skills':skills,'approvals':approvals,'receipts':receipts,'dispatches':dispatches,'remoteCalls':calls,'queries':queries,'actualTarget':target,'modelInference':'NOT_USED_DIRECT_RO_WORKFLOW'}
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_name(output.stem+'-inspect.sql').write_text('\n'.join(statements)+'\n')
    output.with_name(output.stem+'-inspect.tsv').write_text(s['sql']('\n'.join(statements))+'\n')
    output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
    assert fact['status']==expected_status,fact
    assert presented['runId']==run and presented['projectId']==PROJECT and presented['status']==expected_status
    assert len(receipts)==len(dispatches)==len(calls)==len(queries)==expected_calls,evidence
    assert bundles and any(r.get('skillId')==SKILL for b in bundles for r in json.loads(b['refs'])),bundles
    if workflow == 'ops-acceptance-skill-binding':
        definitions = s['api']('/api/v1/admin/ops-agents/' + workflow + '/versions', token=s['token'])
        definition = next(d for d in definitions if d['version'] == fact['version'])
        assert definition['lifecycle'] == 'PUBLISHED' and definition['definitionHash'] == fact['hash']
        assert definition['skills'] == [SKILL]
        assert all(node.get('skills') == [SKILL] for node in definition['nodes'] if node['nodeId'] in ['version-before', 'version-after'])
        for bundle in bundles:
            matching = [r for r in json.loads(bundle['refs']) if r.get('skillId') == SKILL]
            assert len(matching) == 1 and matching[0]['selectedReason'] == 'REQUESTED_ACTIVE_SKILL', matching
        evidence['explicitBinding'] = {'status': 'PASS', 'workflowVersion': fact['version'],
            'workflowHash': fact['hash'], 'skillId': SKILL, 'selectionReason': 'REQUESTED_ACTIVE_SKILL', 'duplicateBindingsFolded': True}
    versions={x['serviceId']:x['version'] for x in target['services']}
    for receipt in receipts:
        assert receipt['status']=='SUCCEEDED' and hashlib.sha256(receipt['output'].encode()).hexdigest()==receipt['hash']
        envelope=json.loads(receipt['output']); observed=envelope['normalizedContent']
        assert envelope['isError'] is False and envelope['structuredContent']==observed
        query=next(x for x in queries if x['query_id']==observed['queryId'])
        assert observed['status']==query['status']=='AVAILABLE'
        assert observed['queryFingerprint']==query['fingerprint'] and observed['scope']==json.loads(query['scope_json'])
        assert hashlib.sha256(json.dumps(observed,sort_keys=True).encode()).hexdigest()==query['response_sha256']
        assert observed['version']==versions[observed['scope']['serviceId']]
    if expected_status=='FAILED':
        assert 'SKILL_RUNTIME_ACCESS_REVOKED' in fact['error'],fact
        assert presented['technicalError']=='SKILL_RUNTIME_ACCESS_REVOKED'
        assert 'Skill 已停用或项目授权已撤回' in presented['summary']
        assert not any(x['nodeId']=='version-after' for x in dispatches)
        assert skills[0]['status']=='DISABLED',skills
        assert any(x['status']=='APPROVED' for x in approvals),approvals
    elif expected_status=='SUCCEEDED':
        assert expected_calls==2 and {x['nodeId'] for x in dispatches}=={'version-before','version-after'}
        assert skills[0]['status']=='ENABLED' and any(x['status']=='APPROVED' for x in approvals)
    else:
        assert any(x['status']=='WAITING' for x in approvals),approvals
    evidence['status']='PASS'; output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':'PASS','run':run,'actualCalls':expected_calls,'runStatus':fact['status'],'skillStatus':skills[0]['status']},ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run',required=True);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--status',required=True,choices=['WAITING_APPROVAL','WAITING','FAILED','SUCCEEDED'])
    p.add_argument('--calls',required=True,type=int)
    p.add_argument('--workflow',choices=['ops-acceptance-skill-revocation','ops-acceptance-skill-binding'], default='ops-acceptance-skill-revocation')
    a=p.parse_args();main(a.run,a.output,a.status,a.calls,a.workflow)
