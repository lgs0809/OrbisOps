#!/usr/bin/env python3
"""Create normal read-only observation workflows over real isolated resources.

The business-source project is separate from frozen development/holdout cases.
No Episodes, acceptances, successful runs or Skill sources are seeded here.
Existing project, policies, definitions and target seed state are preserved.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import runpy
import subprocess
import time
import urllib.parse

ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-closure-business-a'
def seed(service,delay_ms=0):
    source='''import http.client,json,os,sys
service=sys.argv[1]
body={'projectId':'ops-closure-business-a','resourceKey':'service://'+service+'/test','version':'observation-initial','configuration':{'failEvery':0,'delayMs':int(sys.argv[2])}}
connection=http.client.HTTPConnection('127.0.0.1',8993,timeout=20)
connection.request('POST','/control/seed',json.dumps(body),{'Authorization':'Bearer '+os.environ['CLOSURE_CONTROL_TOKEN'],'Content-Type':'application/json'})
response=connection.getresponse(); result={'httpStatus':response.status,'state':json.loads(response.read())};print(json.dumps(result));connection.close()
'''
    process=subprocess.run(['docker','exec','-i','orbisops-acceptance-platform-closure-test-1','python3','-',service,str(delay_ms)],input=source,text=True,capture_output=True,check=True,timeout=30)
    result=json.loads(process.stdout)
    if result['httpStatus']!=200:raise RuntimeError('Target seed changed or unavailable; retained')
    return result
def prepare(output,count,shared_agent=False,first=1,delay_ms=0):
    if output.exists():raise ValueError('Preserve prior evidence')
    h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));api,token=h['api'],h['token']
    spec=importlib.util.spec_from_file_location('closure_graph_comparison',ROOT/'scripts/run-platform-inspection-eval.py')
    common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
    api=common.configuration_api
    users=api('/api/v1/admin/admin-user/query-all',token=token)
    actor=next(u for u in users if u['username']=='ops_acceptance_admin')
    projects=api('/api/v1/admin/ops-projects/snapshot',token=token)['projects']
    if not any(p['projectId']==PROJECT for p in projects):
        api('/api/v1/admin/ops-projects/projects','POST',{'projectId':PROJECT,'name':'实际隔离资源只读观察与来源验收','owner':actor['username'],
            'description':'ACTUAL_LOCAL_RESOURCE_OBSERVATIONS; not frozen eval cases. Native acceptance required before any Skill source.','environments':['test','prod']},token)
    policy_path='/api/v1/admin/model-default-policy?projectId='+PROJECT
    default_policy=api(policy_path,token=token)
    if not default_policy.get('defaultChatModelId'):
        default_policy=api(policy_path,'PUT',{**default_policy,'defaultChatModelId':'ops-acceptance-luna','status':'ENABLED'},token)
    if default_policy.get('status')!='ENABLED' or default_policy.get('defaultChatModelId') not in ['ops-acceptance-luna','ops-acceptance-terra']:
        raise ValueError('Existing business model policy differs from the authorized model pair; retained')
    result={'status':'PREPARING','projectId':PROJECT,'scope':'ACTUAL_LOCAL_RESOURCE_CONFIGURATION_ONLY_NO_ACCEPTANCE',
        'resourceSourceSha256':hashlib.sha256((ROOT/'scripts/fixtures/platform-closure-resource.py').read_bytes()).hexdigest(),
        'mcpSourceSha256':hashlib.sha256((ROOT/'scripts/fixtures/platform-closure-mcp.py').read_bytes()).hexdigest(),
        'sharedAgentVersions':shared_agent,'formalBenchmarkCases':0,'defaultModelPolicy':default_policy,'definitions':[]}
    result['resourceCondition']={'firstIndex':first,'lastIndex':count,'delayMs':delay_ms,
        'independentFaultMechanisms':1,'meaningfulCondition':'ACTUAL_HTTP_DELAY' if delay_ms else 'BASELINE_ZERO_CONFIGURED_DELAY'}
    output.parent.mkdir(parents=True,exist_ok=True)
    for index in range(first,count+1):
        service='closure-observation-'+str(index).zfill(2)
        target=seed(service,delay_ms);path='/api/v1/admin/ops/projects/'+PROJECT;name='Actual closure observation '+service
        endpoint='http://127.0.0.1:8995/mcp?'+urllib.parse.urlencode({'projectId':PROJECT,'service':service})
        integrations=[m for m in api(path+'/tools',token=token) if m['mcpName']==name]
        if not integrations:
            api('/api/v1/admin/ops/tool-executions','POST',{'projectId':PROJECT,'toolsetId':'capability.manage','toolName':'mcp_server_import',
                'userId':actor['userId'],'authenticatedUsername':actor['username'],'executionScope':'PRE_APPROVAL_WORKFLOW',
                'runId':'closure-observation-import-'+service,'arguments':{'sourceUrl':endpoint,'capabilityName':name,
                    'credentialRef':'${env:OPS_ACCEPTANCE_MCP_TOKEN}','transportType':'streamable-http'}},token)
            integrations=[m for m in api(path+'/tools',token=token) if m['mcpName']==name]
        if len(integrations)!=1:raise RuntimeError('Ambiguous integration; preserved')
        integration=integrations[0]
        if integration['transportConfig']['endpoint']!=endpoint or integration['status'] not in ['PENDING_REVIEW','ENABLED']:
            raise RuntimeError('Changed integration; preserved')
        policies=[p for p in api(path+'/mcp-tool-policies',token=token) if p['mcpId']==integration['mcpId']]
        # Mutating capabilities remain unapproved in this read-only source workflow.
        allowed={'test_read_state','test_verify_observation'}
        for tool in allowed:
            policy=next(p for p in policies if p['toolName']==tool)
            expected={'readOnly':True,'riskLevel':'LOW','effectType':'READ_EXTERNAL_STATE','effectScope':'TARGET_RESOURCE_READ',
                'mutability':'READ_ONLY','capability':'READ_ONLY','allowedActions':[tool.upper()],'investigateAllowed':True,
                'prepareAllowed':False,'landAllowed':False,'requiresApprovedPackage':False,'requiresHumanApproval':False,
                'requiresDryRun':False,'requiresRollbackPlan':False,'disclosureTier':'CORE',
                'argumentPolicy':{'resourceBinding':{'resourceScope':'service://'+service+'/test','targetEnvironment':'test'}}}
            if policy['status']=='PENDING_REVIEW':
                policy=api(path+'/mcp-tool-policies/'+policy['policyId']+'/approve','POST',{**expected,
                    'reason':'用户已授权实际隔离资源验收；源码仅读取绑定资源配置及真实订单GET，不暴露种子/故障控制；测试和模拟生产配置写均不在此流程批准。'},token)
            if policy['status']!='ACTIVE' or any(policy.get(k)!=v for k,v in expected.items()):raise RuntimeError('Existing policy differs; retained')
        if integration['status']!='ENABLED':api(path+'/tools/'+integration['mcpId']+'/status','PATCH',{'status':'ENABLED'},token)
        actions=[{'mcpId':integration['mcpId'],'remoteToolName':tool,'arguments':{'projectId':PROJECT,'service':service},
            'structuredOutputKey':'baseline' if tool=='test_read_state' else 'observation','outputMode':'MCP_EVIDENCE_REFERENCE'} for tool in ['test_read_state','test_verify_observation']]
        value={'agentId':'ops-closure-observation-'+str(index).zfill(2)+'-v1','schemaVersion':1,'projectId':PROJECT,
            'name':'资源观察 '+str(index).zfill(2),'engine':'GRAPH','definitionKind':'SPECIALIZED_WORKFLOW',
            'workflowInvocationMode':'MANUAL_ONLY','workflowAutoSelectEnabled':False,'queryRewriteEnabled':False,'ragEnabled':False,
            'description':'实际隔离配置前后观察；自然语言任务输入；业务验收单独确认，不自动宣布完成。',
            'instruction':'按正常用户目标读取实际绑定服务。只执行当前发布的只读工具，不修改测试或模拟生产配置。',
            'startNodeId':'start','nodes':[{'nodeId':'start','type':'START','config':{'inputKeys':['query'],'maxRealToolCalls':3}},
                {'nodeId':'observe','type':'AGENT','mode':'DIRECT','agent':'ops-closure-observer','mcpIds':[integration['mcpId']],
                    'outputKey':'resourceObservation','config':{'inheritProjectCapabilities':False,'actions':actions}},
                {'nodeId':'report','type':'AGENT','mode':'LLM','agent':'ops-closure-observer','modelId':'ops-acceptance-terra',
                    'mcpIds':[],'outputKey':'observationReport','instruction':'根据完整实际baseline和observation回执，用自然中文列出项目、test环境、服务资源身份；说明前后版本、epoch、配置摘要及真实观察开始结束时间，并核对20次订单GET和errorCount。只说明这些回执覆盖的区间，不宣称外部世界或全时段没有修改。执行结束仍等待用户业务验收。不得执行工具、发布或编造数据。',
                    'config':{'inheritProjectCapabilities':False,'contextInputs':['query','workflowData_baseline','workflowData_observation']}},
                {'nodeId':'end','type':'END','config':{'outputKeys':['observationReport']}}],
            'edges':[{'from':a,'to':b,'conditionType':'always','condition':'always'} for a,b in [('start','observe'),('observe','report'),('report','end')]]}
        if shared_agent:value['agentId']='ops-closure-observation-01-v1'
        versions=api('/api/v1/admin/ops-agents/'+value['agentId']+'/versions',token=token)
        matches=[version for version in versions
            if common.normalized_graph_parts(version)==common.normalized_graph_parts(value)
            and all(version.get(key)==value[key] for key in value.keys()-{'nodes','edges'})]
        if len(matches)>1:raise RuntimeError('Ambiguous matching versions; retained')
        if matches:
            saved=matches[0]
        else:
            # A different resource gets a new immutable version of the shared
            # workflow. A changed existing resource configuration is never replaced.
            if versions and (not shared_agent or any(v.get('name')==value['name'] for v in versions)):
                raise RuntimeError('Existing resource workflow differs; retained')
            saved=api('/api/v1/admin/ops-agents/drafts','POST',value,token)
        address='/api/v1/admin/ops-agents/'+saved['agentId']+'/versions/'+str(saved['version'])
        if saved['lifecycle']=='DRAFT':saved=api(address+'/validate','POST',{},token)
        if saved['lifecycle']=='VALIDATED':saved=api(address+'/publish','POST',{},token)
        if saved['lifecycle']!='PUBLISHED':raise RuntimeError('Existing definition is not published; retained')
        result['definitions'].append({'service':service,'targetSeed':target,'mcpId':integration['mcpId'],
            **{k:saved[k] for k in ['agentId','version','definitionHash']},'actorUserId':actor['userId']})
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'service':service,'stage':'PUBLISHED_CONFIGURATION_ONLY'}),flush=True)
        time.sleep(1.1)
    result['status']='READY';output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--count',type=int,default=1)
    parser.add_argument('--shared-agent',action='store_true',help='New resources use new immutable published versions; no existing version is overwritten')
    parser.add_argument('--first',type=int,default=1,help='First retained resource index; --count remains the last index')
    parser.add_argument('--delay-ms',type=int,default=0,help='Seed a new resource with bounded actual per-request delay, never replace existing seed state')
    args=parser.parse_args()
    if not 1<=args.first<=args.count<=30 or args.count-args.first+1>21:parser.error('At most21 resources per invocation, with retained indexes1..30')
    if not 0<=args.delay_ms<=500:parser.error('Bounded actual delay0..500ms')
    prepare(args.output,args.count,args.shared_agent,args.first,args.delay_ms)
