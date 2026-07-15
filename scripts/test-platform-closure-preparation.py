#!/usr/bin/env python3
"""Real isolated MCP/native ledger validation. No model or task acceptance claim."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

PEER = r'''
import datetime as dt,http.client,json,os,urllib.parse,uuid
project='ops-acceptance-a';service='closure-preparation-component-'+uuid.uuid4().hex[:12]
scope={'projectId':project,'resourceKey':'service://'+service+'/test'}
def request(port,path,token,body=None,extra=None):
 c=http.client.HTTPConnection('127.0.0.1',port,timeout=20)
 try:
  c.request('POST' if body is not None else 'GET',path,json.dumps(body) if body is not None else None,{'Authorization':'Bearer '+token,'Content-Type':'application/json',**(extra or {})})
  r=c.getresponse();return {'httpStatus':r.status,'headers':dict(r.getheaders()),'body':json.loads(r.read())}
 finally:c.close()
target=lambda path,body=None:request(8993,path,os.environ['PREPARE_CONTROL_TOKEN'],body)
seed=target('/control/seed',{**scope,'version':'native-baseline','configuration':{'delayMs':2,'failEvery':4}})
if seed['httpStatus']!=200 or seed['body']['epoch']!=0:raise ValueError('Preserve seed collision')
path='/mcp?'+urllib.parse.urlencode({'projectId':project,'service':service})
init=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],{'jsonrpc':'2.0','id':'init-'+uuid.uuid4().hex,'method':'initialize','params':{'protocolVersion':'2024-11-05','clientInfo':{'name':'native-validation-component','version':'1'},'capabilities':{}}})
session=init['headers']['Mcp-Session-Id'];results=[]
def call(tool,extra=None):
 rpc='validation-'+uuid.uuid4().hex
 x=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],{'jsonrpc':'2.0','id':rpc,'method':'tools/call','params':{'name':tool,'arguments':{'projectId':project,'service':service,**(extra or {})}}},{'Mcp-Session-Id':session})
 results.append({'rpcId':rpc,'tool':tool,'response':x});return x['body']['result']
criteria={'baselineVersion':'native-baseline','candidateVersion':'native-candidate','baselineConfiguration':{'delayMs':2,'failEvery':4},'candidateConfiguration':{'delayMs':0,'failEvery':0}}
missing=call('test_verify_preparation',criteria)
write=call('test_apply_configuration',{'expectedVersion':'native-baseline','expectedEpoch':0,'version':'native-candidate','configuration':criteria['candidateConfiguration']})
orders=call('test_check_orders');rollback=call('test_apply_configuration',{'expectedVersion':'native-candidate','expectedEpoch':1,'version':'native-baseline','configuration':criteria['baselineConfiguration']})
evidence_path='/evidence?'+urllib.parse.urlencode(scope)
before=target(evidence_path)['body'];passed=call('test_verify_preparation',criteria);after=target(evidence_path)['body']
wrong_version=call('test_verify_preparation',{**criteria,'candidateVersion':'wrong-version'})
wrong_config=call('test_verify_preparation',{**criteria,'candidateConfiguration':{'delayMs':1,'failEvery':0}})
changed=call('test_apply_configuration',{'expectedVersion':'native-baseline','expectedEpoch':2,'version':'native-baseline','configuration':criteria['baselineConfiguration']})
stale=call('test_verify_preparation',criteria)
scope_denied=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],{'jsonrpc':'2.0','id':'scope-'+uuid.uuid4().hex,'method':'tools/call','params':{'name':'test_verify_preparation','arguments':{'projectId':'ops-platform-closure-dev','service':service,**criteria}}},{'Mcp-Session-Id':session})['body']['result']
native=passed['structuredContent'];trace_map={r['trace_id']:r for r in before['request']}
checks={
 'missingHistoryFailsClosed':missing['isError'] and missing['structuredContent']['status']=='FAILED',
 'actualCandidateWriteAndRollback':not write['isError'] and not rollback['isError'] and rollback['structuredContent']['epoch']==2,
 'twentyActualCandidateHttpGets':not orders['isError'] and len(native['requests'])==20 and all(r['trace_id'] in trace_map and trace_map[r['trace_id']]==r and r['status']==200 and r['version']=='native-candidate' for r in native['requests']),
 'nativeVerificationPasses':not passed['isError'] and native['status']=='PASSED' and all(native['checks'].values()),
 'readOnlyValidationDoesNotWrite':before['receipt']==after['receipt'] and before['dispatch']==after['dispatch'] and before['command_evidence']==after['command_evidence'] and before['state']['epoch']==after['state']['epoch']==2,
 'wrongCandidateVersionFails':wrong_version['isError'] and wrong_version['structuredContent']['checks']['candidateMatchesRequested'] is False,
 'wrongCandidateConfigurationFails':wrong_config['isError'] and wrong_config['structuredContent']['checks']['candidateMatchesRequested'] is False,
 'subsequentSameVersionEpochChangeFails':not changed['isError'] and changed['structuredContent']['epoch']==3 and stale['isError'],
 'crossProjectRejected':scope_denied['isError'] and scope_denied['content'][0]['text']=='BOUND_RESOURCE_SCOPE_MISMATCH',
 'atomicCommandEvidenceRecomputes':len(before['command_evidence'])==2 and all(json.loads(r['command_json'])['executionKey']==r['execution_key'] for r in before['command_evidence']),
 'allOutputNativeIdentityBound':native['projectId']==project and native['resourceKey']==scope['resourceKey'] and native['environment']=='test' and native['readOnly'] is True}
print(json.dumps({'projectId':project,'service':service,'checks':checks,'results':results,'beforeValidation':before,'afterValidation':after,'scopeRejected':scope_denied},ensure_ascii=False))
'''

def main(output):
    if output.exists():raise ValueError('Retain existing evidence')
    started=dt.datetime.now(dt.timezone.utc).isoformat()
    result=subprocess.run(['docker','exec','-i','orbisops-acceptance-platform-closure-mcp-1','python3','-'],input=PEER,text=True,capture_output=True,timeout=120)
    root=Path(__file__).resolve().parents[1]
    report={'scope':'ACTUAL_ISOLATED_MCP_SQLITE_HTTP_COMPONENT_ONLY','startedAt':started,'completedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'modelRequests':0,'taskAcceptancesCreated':0,'formalBenchmarkSamples':0,'exitCode':result.returncode,'sourceHashes':{p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in ['scripts/fixtures/platform-closure-mcp.py','scripts/fixtures/platform-closure-resource.py','scripts/test-platform-closure-preparation.py']}}
    if result.returncode:report.update(status='FAIL_HARNESS_OR_COMPONENT',error=result.stderr[-3000:])
    else:
        report.update(json.loads(result.stdout));report['status']='PASS' if all(report['checks'].values()) else 'FAIL'
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'checks':report.get('checks'),'exitCode':result.returncode}));return report['status']=='PASS'

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args();raise SystemExit(0 if main(a.output) else 1)
