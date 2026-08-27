#!/usr/bin/env python3
"""Real child-run failure and approval/restart recovery, using the published B business graph."""
from pathlib import Path
import argparse
import copy
import json
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
business = runpy.run_path(str(ROOT / 'scripts/test-business-workflow-runtime.py'))
mechanism = runpy.run_path(str(ROOT / 'scripts/test-workflow-data-runtime.py'))
api, token, sql, rows, quoted, until = [support[k] for k in ('api','token','sql','rows','quoted','until')]
PROJECT = support['PROJECT']


def checkpoint(run):
    records = rows('SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=' + quoted(run)
                   + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")
    return records[0]['state'] if records else {}


def child_count(parent):
    return int(sql('SELECT COUNT(*) FROM ai_ops_agent_run WHERE '
                   + "JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.metadata.parentRunId'))=" + quoted(parent)))


def business_start(graph, query):
    session = api('/api/v1/user/chat/session', 'POST', {'projectId': PROJECT, 'agentId': graph['agentId'],
                  'agentVersion': graph['version'], 'title': '验收服务 A4 · 自然语言巡检与子工作流恢复'}, token)
    response = api(f'/api/v1/user/chat/sessions/{session}/messages', 'POST', {'projectId': PROJECT,
                   'query': query, 'mode': 'AGENT', 'engine': 'GRAPH', 'agentDefinitionId': graph['agentId'],
                   'agentVersion': graph['version'], 'metadata': {'executionType': 'WORKFLOW',
                   'fixture': 'OPS-04-real-business-child-recovery'}}, token, timeout=720)
    return session, response['metadata']['runId']


def business_main(parent_version=None, browser_approval=False, output=None):
    versions = api('/api/v1/admin/ops-agents/ops-business-inspection-a/versions', token=token)
    published_versions = [v for v in versions if v['lifecycle']=='PUBLISHED']
    if parent_version is None:
        published = max(published_versions, key=lambda v: v['version'])
    else:
        matches = [v for v in published_versions if v['version'] == parent_version]
        if len(matches) != 1:
            raise ValueError('The explicitly requested published parent version is unavailable; nothing changed')
        published = matches[0]
    graph = copy.deepcopy(published)
    graph['agentId'] = 'ops04-child-recovery-' + uuid.uuid4().hex[:10]
    graph['name'] = 'OPS-04 子工作流重启验收（隔离机制图）'
    graph['description'] = '仅增加自然人工审批暂停点以验证 B 子运行持久化恢复；不修改正式巡检发布图或业务数据'
    graph['nodes'].append({'nodeId':'pause','type':'HUMAN_APPROVAL','description':'批准继续汇总已完成的只读 B 调查，不执行业务变更',
                           'config':{'timeoutSeconds':900}})
    for edge in graph['edges']:
        if edge['from']=='investigate' and edge['to']=='summary': edge['to']='pause'
    graph['edges'].append({'from':'pause','to':'summary','conditionType':'always','condition':'always'})
    graph = mechanism['save_and_publish'](graph)
    # Current published graphs resolve ordinary language internally. Historical definitions
    # remain frozen; protocol fixtures exercise structured binding in their separate scenario.
    query = '请只读巡检验收服务 A4 最近五分钟的可达性、请求数、错误率和延迟；如果存在异常，请继续调用告警调查工作流，结合日志与实际 SQL 执行计划核对证据。不要修复或发布。'
    session, parent = business_start(graph,query)
    path = f'/api/v1/agent/chat/runs/{parent}/workflow-approval?projectId={PROJECT}'
    pending = support['waiting_approval'](parent)
    state = checkpoint(parent)
    reference = state['variables']['nodeOutput:investigate']['workflowData_investigation']
    child = reference['childRunId']
    before = business['inspect'](child)
    assert before['run']['status']=='SUCCEEDED' and len(before['remoteCalls'])==4
    assert child_count(parent)==1
    recovery = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    until(support['ready'])
    assert api(path,token=token)['approvalId']==pending['approvalId']
    if browser_approval:
        progress = {'status':'WAITING_FOR_BROWSER_APPROVAL','sessionId':session,'runId':parent,
                    'query':query,'approvalId':pending['approvalId'],'childBeforeRestart':before,
                    'namespaceRecovery':recovery,'graphVersion':graph['version'],
                    'graphHash':graph['definitionHash'],'sourceParentVersion':published['version']}
        output.with_name(output.stem+'-pending.json').write_text(json.dumps(progress,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps({'status':progress['status'],'sessionId':session,'runId':parent},ensure_ascii=False),flush=True)
    else:
        api(f'/api/v1/agent/chat/runs/{parent}/workflow-approval/decision?projectId={PROJECT}', 'POST',
            {'approvalId':pending['approvalId'],'decision':'APPROVE'},token)
    after = business['inspect'](parent)
    assert after['run']['status']=='SUCCEEDED' and after['run']['epoch']>=2
    assert after['childInvestigation']['run']==before['run']
    assert after['childInvestigation']['physicalDispatches']==before['physicalDispatches']
    assert child_count(parent)==1

    assert after['report']['status']=='UNHEALTHY', after['report']
    assert after['checks']['configuredModelsVerified'] and before['checks']['configuredModelsVerified']
    return {'result':'PASS','fixture':'isolated approval pause added to published A; actual B and actual natural-language model resolution',
            'query':query,'sessionId':session,'browserApproval':browser_approval,
            'approvalId':pending['approvalId'],'parentAfterRestart':after,'childBeforeRestart':before,
            'sourceParentVersion': published['version'], 'sourceParentDefinitionHash': published['definitionHash'],
            'namespaceRecovery': recovery,
            'limitations':['Child failure before approval is tested separately by the protocol scenario.',
                          'An unhealthy inspection and a completed investigation do not prove remediation or a confirmed root cause.'],
            'checks':{'oneDurableChildAfterRestart':True,'noRepeatedChildRpc':True,'exactChildVersionHashPreserved':True,
                      'approvalSubmittedNormally':True,'ordinaryLanguageResolvedByConfiguredModel':True,
                      'actualUnhealthyInspectionInvestigatedWithSql':True}}


def protocol_main(mcp_name):
    """Independent recovery proof, without relying on a changing business window or a model verdict."""
    tag = uuid.uuid4().hex[:10]
    child_definition = mechanism['definition'](mcp_name)
    child_definition.update(agentId='ops-acceptance-sub-child-' + tag, name='子工作流只读协议恢复 · ' + tag)
    child_definition['nodes'] = [n for n in child_definition['nodes'] if n['nodeId'] in ('start', 'mcp-read', 'followup', 'end')]
    child_definition['edges'] = [{'from': a, 'to': b, 'conditionType': 'always', 'condition': 'always'}
                                 for a,b in [('start','mcp-read'),('mcp-read','followup'),('followup','end')]]
    child_v1 = mechanism['save_and_publish'](child_definition)
    parent_definition = copy.deepcopy(child_definition)
    parent_definition.update(agentId='ops-acceptance-sub-parent-' + tag, name='子工作流冻结及审批恢复 · ' + tag)
    parent_definition['nodes'] = [next(n for n in child_definition['nodes'] if n['nodeId']=='start'),
        {'nodeId':'investigate','type':'SUB_WORKFLOW','agent':child_v1['agentId'],
         'config':{'versionPolicy':'PINNED_VERSION','version':child_v1['version'],'structuredOutputKey':'investigation'}},
        {'nodeId':'pause','type':'HUMAN_APPROVAL','description':'批准继续汇总已经完成的隔离只读协议结果',
         'config':{'timeoutSeconds':900}},
        {'nodeId':'end','type':'END','config':{'outputKeys':['workflowData_investigation']}}]
    parent_definition['edges'] = [{'from':a,'to':b,'conditionType':'always','condition':'always'}
                                   for a,b in [('start','investigate'),('investigate','pause'),('pause','end')]]
    parent = mechanism['save_and_publish'](parent_definition)
    query={'first':'ops04-sub-first-'+tag,'second':'ops04-sub-second-'+tag,'service':'normal'}
    parent_run = mechanism['start'](parent, query)
    pending = support['waiting_approval'](parent_run)
    reference=checkpoint(parent_run)['variables']['nodeOutput:investigate']['workflowData_investigation']
    child_run=reference['childRunId']
    before=support['facts'](child_run)
    assert before['status']=='SUCCEEDED' and before['version']==child_v1['version'] and before['hash']==child_v1['definitionHash']
    assert reference['workflowDefinitionHash']==before['hash'] and reference['workflowVersion']==before['version']
    assert child_count(parent_run)==1
    dispatch_query="SELECT JSON_OBJECT('rpcId',request_id,'node',node_id,'limit',budget_limit) FROM ai_ops_workflow_tool_dispatch WHERE run_id="+quoted(child_run)+' ORDER BY id'
    dispatch_before=rows(dispatch_query)
    assert len(dispatch_before)==2 and all(d['limit']==2 for d in dispatch_before)
    remote_before=[c for c in support['peer_evidence']()['requests'] if c['method']=='tools/call' and c['test_id'] in (query['first'],query['second'])]
    assert {c['rpc_id'] for c in remote_before}=={d['rpcId'] for d in dispatch_before}
    # A new current definition must not replace the completed child's frozen definition during parent recovery.
    child_v2_definition=copy.deepcopy(child_definition)
    child_v2_definition['nodes'][-2]['config']['actions'][0]['arguments']['mode']='multi'
    child_v2=mechanism['save_and_publish'](child_v2_definition)
    assert child_v2['version']>child_v1['version'] and child_v2['definitionHash']!=child_v1['definitionHash']
    recovery=runpy.run_path(str(ROOT/'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    path=f'/api/v1/agent/chat/runs/{parent_run}/workflow-approval?projectId={PROJECT}'
    assert api(path,token=token)['approvalId']==pending['approvalId']
    api(f'/api/v1/agent/chat/runs/{parent_run}/workflow-approval/decision?projectId={PROJECT}','POST',
        {'approvalId':pending['approvalId'],'decision':'APPROVE'},token)
    after=mechanism['terminal'](parent_run)
    assert after['status']=='SUCCEEDED' and after['epoch']>=2
    assert support['facts'](child_run)==before and rows(dispatch_query)==dispatch_before and child_count(parent_run)==1
    remote_after=[c for c in support['peer_evidence']()['requests'] if c['method']=='tools/call' and c['test_id'] in (query['first'],query['second'])]
    assert remote_after==remote_before
    # The child genuinely fails argument binding; the parent must not reach its approval or END.
    failed_run=mechanism['start'](parent, {'second':'ops04-sub-missing-'+tag,'service':'normal'})
    failed=mechanism['terminal'](failed_run)
    children=rows("SELECT JSON_OBJECT('runId',run_id,'status',status) FROM ai_ops_agent_run WHERE "
                  +"JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.metadata.parentRunId'))="+quoted(failed_run))
    assert failed['status']=='FAILED' and len(children)==1 and children[0]['status']=='FAILED'
    assert 'nodeOutput:end' not in checkpoint(failed_run).get('variables',{})
    assert int(sql('SELECT COUNT(*) FROM ai_ops_workflow_approval WHERE run_id='+quoted(failed_run)))==0
    for run in (failed_run,children[0]['runId']):
        assert int(sql('SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id='+quoted(run)))==0
    return {'result':'PASS','fixture':'real local protocol child recovery; not business or model-quality acceptance',
            'parentAfterRestart':after,'childBeforeRestart':before,'childCurrentVersion':child_v2['version'],
            'approvalId':pending['approvalId'],'dispatches':dispatch_before,'remoteCalls':remote_after,
            'failedParent':failed,'failedChild':children[0],'namespaceRecovery':recovery,
            'checks':{'oneDurableChildAfterRestart':True,'noRepeatedChildRpc':True,'exactChildVersionHashPreserved':True,
                      'newCatalogVersionDoesNotReplaceFrozenChild':True,'approvalSubmittedNormally':True,
                      'childFailurePropagatesWithoutEndOrToolCalls':True}}


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--parent-version', type=int, help='Explicit historical frozen business topology; default uses the latest publication')
    parser.add_argument('--scenario', choices=['business','protocol'], default='business')
    parser.add_argument('--browser-approval',action='store_true',help='After restart, wait for approval through the real browser UI (business scenario only)')
    parser.add_argument('--mcp-name',default=support['mcp_name'],help='Exact reviewed MCP identity for the protocol scenario')
    args=parser.parse_args()
    if args.output.exists():
        raise ValueError('Use a fresh evidence path')
    if args.browser_approval and args.scenario!='business':
        raise ValueError('Browser approval applies to the natural-language business scenario')
    try: result=protocol_main(args.mcp_name) if args.scenario=='protocol' else business_main(args.parent_version,args.browser_approval,args.output)
    except Exception as error:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps({'result':'FAIL','error':type(error).__name__+': '+str(error)},ensure_ascii=False,indent=2))
        raise
    finally:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.with_suffix('.sql').write_text('-- Actual read-only parent/child evidence queries.\n' + '\n'.join(support['sql_queries']) + '\n')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    parent_result = result['parentAfterRestart']
    parent_run = parent_result.get('runId') or parent_result['run']['runId']
    print(json.dumps({'result':'PASS','parentRun':parent_run,'checks':result['checks']},ensure_ascii=False))
