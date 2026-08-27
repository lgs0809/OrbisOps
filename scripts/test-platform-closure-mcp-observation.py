#!/usr/bin/env python3
"""Actual MCP/HTTP observation aggregates; no model, task acceptance or formal samples."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PEER = r'''
import http.client,json,os,sys,urllib.parse,uuid
project=sys.argv[2]
delay_ms=int(sys.argv[1])
def request(port,path,token,body=None,extra=None):
 c=http.client.HTTPConnection('127.0.0.1',port,timeout=20)
 try:
  c.request('POST' if body is not None else 'GET',path,json.dumps(body) if body is not None else None,
   {'Authorization':'Bearer '+token,'Content-Type':'application/json',**(extra or {})})
  r=c.getresponse();return {'status':r.status,'headers':dict(r.getheaders()),'body':json.loads(r.read())}
 finally:c.close()
results=[]
for fail_every,label in [(0,'healthy'),(4,'business-failure')]:
 service='closure-aggregate-component-'+label+'-'+uuid.uuid4().hex[:12]
 scope={'projectId':project,'resourceKey':'service://'+service+'/test'}
 seed=request(8993,'/control/seed',os.environ['PREPARE_CONTROL_TOKEN'],
  {**scope,'version':'observation-initial','configuration':{'failEvery':fail_every,'delayMs':delay_ms}})
 if seed['status']!=200:raise RuntimeError('Seed collision must not overwrite existing data: '+str(seed['status']))
 path='/mcp?'+urllib.parse.urlencode({'projectId':project,'service':service})
 initialize=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],
  {'jsonrpc':'2.0','id':'initialize-'+uuid.uuid4().hex,'method':'initialize',
   'params':{'protocolVersion':'2024-11-05','clientInfo':{'name':'actual-component-aggregate','version':'1'},'capabilities':{}}})
 session=initialize['headers']['Mcp-Session-Id'];rpc='aggregate-'+uuid.uuid4().hex
 call=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],
  {'jsonrpc':'2.0','id':rpc,'method':'tools/call','params':{'name':'test_verify_observation',
   'arguments':{'projectId':project,'service':service}}},{'Mcp-Session-Id':session})
 result=call['body']['result'];observed=result['structuredContent']
 target=request(8993,'/evidence?'+urllib.parse.urlencode(scope),os.environ['PREPARE_CONTROL_TOKEN'])['body']
 audit=[r for r in target['audit_request'] if r['rpc_id']==rpc and r['path']=='/orders']
 actual={r['trace_id']:r for r in target['request']}
 errors=0 if fail_every==0 else 5
 checks={
  'twentyActualGetRequests':len(audit)==len(observed['requests'])==20 and all(r['method']=='GET' for r in audit),
  'allReceiptsMatchActualHttpStatus':all(r['traceId'] in actual and r['httpStatus']==actual[r['traceId']]['status'] for r in observed['requests']),
  'aggregateRecomputes':observed['allRequestsSucceeded']==all(r['httpStatus']==200 for r in observed['requests']) and observed['errorCount']==sum(r['httpStatus']!=200 for r in observed['requests'])==errors,
  'exposedMethodPathActual':observed['requestMethod']=='GET' and observed['routeDefinition']=='/orders' and all(r['method']=='GET' and r['path']=='/orders' for r in observed['requests']),
  'businessFailureCannotBeObservationPass':result['isError']==(fail_every!=0) and observed['status']==('PASSED' if fail_every==0 else 'FAILED'),
  'readOnlyNoConfigurationDispatch':not target['receipt'] and not target['dispatch'] and target['state']['epoch']==0,
  'actualDelayInEveryRequest':all(actual[r['traceId']]['duration_ms']>=delay_ms for r in observed['requests']),
  'allNativeDurationsMatchPersistedReceipts':all(r['durationMs']==actual[r['traceId']]['duration_ms'] for r in observed['requests']),
  'minimumAndTotalDurationsRecompute':observed['minimumRequestDurationMs']==min(r['durationMs'] for r in observed['requests']) and observed['totalRequestDurationMs']==sum(r['durationMs'] for r in observed['requests']),
  'delayConditionInActualNativeDuration':observed['minimumRequestDurationMs']>=delay_ms and observed['totalRequestDurationMs']>=20*delay_ms,
  'delayConfigurationUnchanged':all(observed[k]['configuration']['delayMs']==delay_ms for k in ['beforeState','afterState']),
  'unchangedConfigurationInObservedInterval':observed['configurationUnchanged'] and all(observed['beforeState'][k]==observed['afterState'][k] for k in ['resourceKey','projectId','environment','version','epoch','configurationDigest'])}
 results.append({'service':service,'rpcId':rpc,'checks':checks,'response':call,'target':target})
print(json.dumps(results,ensure_ascii=False))
'''

def main(output,delay_ms=0,project='ops-closure-business-a'):
    if output.exists(): raise ValueError('Prior evidence retained')
    if not 0 <= delay_ms <= 500:raise ValueError('Actual target delay must be 0..500 ms')
    report={'scope':'ACTUAL_MCP_HTTP_SQLITE_COMPONENT_ONLY','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
        'modelRequests':0,'newFormalBenchmarkSamples':0,'platformTaskAcceptance':'NOT_ASSERTED','actualDelayMs':delay_ms,'projectId':project,
        'runnerSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'sourceHashes':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in [ROOT/'scripts/fixtures/platform-closure-mcp.py',ROOT/'scripts/fixtures/platform-closure-resource.py']}}
    result=subprocess.run(['docker','exec','-i','orbisops-acceptance-platform-closure-mcp-1','python3','-',str(delay_ms),project],
        input=PEER,text=True,capture_output=True,timeout=90)
    report.update(exitCode=result.returncode,completedAt=dt.datetime.now(dt.timezone.utc).isoformat())
    if result.returncode:
        report.update(status='FAIL_HARNESS_OR_COMPONENT',error=result.stderr[-3000:])
    else:
        report['results']=json.loads(result.stdout)
        report['status']='PASS' if all(all(r['checks'].values()) for r in report['results']) else 'FAIL'
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ['results']},ensure_ascii=False))
    raise SystemExit(0 if report['status']=='PASS' else 1)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--delay-ms',type=int,default=0)
    parser.add_argument('--project',choices=['ops-closure-business-a','ops-acceptance-a'],default='ops-closure-business-a')
    args=parser.parse_args();main(args.output,args.delay_ms,args.project)
