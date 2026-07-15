#!/usr/bin/env python3
"""Bind a new local A resource through normal seed/import/policy APIs.

This does not seed an Episode, approval, package, successful run, Skill or
acceptance. The strict tools have no actor field. Actual model preparation and
independent review must happen subsequently through the normal product flow.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import runpy
import subprocess
import urllib.parse

ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-acceptance-a'

def prepare(output,service):
    if output.exists():raise ValueError('Retain previous evidence')
    if not service.startswith('closure-generic-') or not service.replace('-','').isalnum():raise ValueError('Exact local resource required')
    support=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    spec=importlib.util.spec_from_file_location('config',ROOT/'scripts/run-platform-inspection-eval.py')
    common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
    api,token=common.configuration_api,support['token']
    actor=next(u for u in api('/api/v1/admin/admin-user/query-all',token=token) if u['username']=='ops_acceptance_admin')
    report={'status':'PREPARING_CONFIGURATION_ONLY','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
        'projectId':PROJECT,'service':service,'approvalsCreated':0,'packagesCreated':0,'modelExecutions':0,
        'taskAcceptancesCreated':0,'productionAuthorityExpanded':False,'seeds':[]}
    output.parent.mkdir(parents=True,exist_ok=True)
    def save():output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    save()
    remote='''import http.client,json,os,sys
environment,service=sys.argv[1:]
body={'projectId':'ops-acceptance-a','resourceKey':'service://'+service+'/'+environment,'version':'generic-baseline','configuration':{'delayMs':75,'failEvery':4}}
c=http.client.HTTPConnection('127.0.0.1',8993 if environment=='test' else 8994,timeout=15)
c.request('POST','/control/seed',json.dumps(body),{'Authorization':'Bearer '+os.environ['CLOSURE_CONTROL_TOKEN'],'Content-Type':'application/json'})
r=c.getresponse();print(json.dumps({'httpStatus':r.status,'state':json.loads(r.read())}));c.close()
'''
    for environment in ['test','prod']:
        raw=subprocess.check_output(['docker','exec','-i','orbisops-acceptance-platform-closure-'+environment+'-1',
            'python3','-',environment,service],input=remote,text=True)
        seeded=json.loads(raw)
        if seeded['httpStatus']!=200 or seeded['state']['projectId']!=PROJECT or seeded['state']['version']!='generic-baseline' or seeded['state']['epoch']!=0:
            raise ValueError('Prior resource is retained; fresh untouched identity required')
        report['seeds'].append(seeded);save()
    path='/api/v1/admin/ops/projects/'+PROJECT
    endpoint='http://127.0.0.1:8995/mcp?'+urllib.parse.urlencode({'projectId':PROJECT,'service':service})
    name='Actual generic three-phase '+service
    found=[m for m in api(path+'/tools',token=token) if m['mcpName']==name]
    if not found:
        api('/api/v1/admin/ops/tool-executions','POST',{'projectId':PROJECT,'toolsetId':'capability.manage',
            'toolName':'mcp_server_import','userId':actor['userId'],'authenticatedUsername':actor['username'],
            'executionScope':'PRE_APPROVAL_WORKFLOW','runId':'generic-phase-import-'+service,
            'arguments':{'sourceUrl':endpoint,'capabilityName':name,'credentialRef':'${env:OPS_ACCEPTANCE_MCP_TOKEN}',
                'transportType':'streamable-http'}},token)
        found=[m for m in api(path+'/tools',token=token) if m['mcpName']==name]
    if len(found)!=1 or found[0]['transportConfig']['endpoint']!=endpoint or found[0]['status'] not in ['PENDING_REVIEW','ENABLED']:
        raise ValueError('Changed integration retained')
    mcp=found[0];report['mcpId']=mcp['mcpId'];report['remoteTools']=mcp['remoteTools'];save()
    policies=[p for p in api(path+'/mcp-tool-policies',token=token) if p['mcpId']==mcp['mcpId']]
    reviewed=[]
    for policy in policies:
        tool=policy['toolName'];environment=tool.split('_',1)[0];write=tool.endswith('_apply_configuration')
        prodwrite=write and environment=='prod'
        expected={'effectType':'MUTATE_TARGET_RESOURCE' if prodwrite else 'MUTATE_TEST_RESOURCE' if write else 'READ_EXTERNAL_STATE',
            'effectScope':'TARGET_RESOURCE_WRITE' if prodwrite else 'SANDBOX' if write else 'TARGET_RESOURCE_READ',
            'mutability':'PROD_MUTATING' if prodwrite else 'TEST_MUTATING' if write else 'READ_ONLY',
            'capability':'MUTATING' if write else 'READ_ONLY','allowedActions':[tool.upper()],
            'riskLevel':'HIGH' if prodwrite else 'MEDIUM' if write else 'LOW','readOnly':not write,
            'investigateAllowed':not write,'prepareAllowed':not prodwrite,'landAllowed':environment=='prod',
            'requiresApprovedPackage':prodwrite,'requiresHumanApproval':prodwrite,'requiresDryRun':prodwrite,
            'requiresRollbackPlan':prodwrite,'disclosureTier':'EXTENSION' if write else 'CORE',
            'argumentPolicy':{'resourceBinding':{'resourceScope':'service://'+service+'/'+environment,'targetEnvironment':environment}}}
        if policy['status']=='PENDING_REVIEW':
            policy=api(path+'/mcp-tool-policies/'+policy['policyId']+'/approve','POST',{**expected,
                'reason':'用户已授权本机隔离双环境三阶段验收；test写只在PREPARE，模拟prod写只在原生批准包Landing，CAS/epoch/deadline/owned key不变。资源固定绑定，Schema无actor。'},token)
        if policy['status']!='ACTIVE' or any(policy.get(k)!=v for k,v in expected.items()):raise ValueError('Changed policy retained')
        reviewed.append(policy);report['policies']=reviewed;save()
    if mcp['status']!='ENABLED':api(path+'/tools/'+mcp['mcpId']+'/status','PATCH',{'status':'ENABLED'},token)
    report.update(status='READY_CONFIGURATION_ONLY',finishedAt=dt.datetime.now(dt.timezone.utc).isoformat(),
        sourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    save();print(json.dumps({'status':report['status'],'mcpId':report['mcpId'],'policies':len(reviewed),'modelExecutions':0}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--service',default='closure-generic-23');a=p.parse_args();prepare(a.output,a.service)
