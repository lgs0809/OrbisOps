#!/usr/bin/env python3
"""Actual scoped MCP epoch-CAS/receipt contract; private component, no platform approval or models."""
import argparse,datetime as dt,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PEER=r"""
import http.client,json,os,urllib.parse,uuid,datetime as dt
project='ops-platform-closure-dev'
def request(port,path,token,body=None,extra=None):
 c=http.client.HTTPConnection('127.0.0.1',port,timeout=20)
 try:
  c.request('POST' if body is not None else 'GET',path,json.dumps(body) if body is not None else None,{'Authorization':'Bearer '+token,'Content-Type':'application/json',**(extra or {})})
  r=c.getresponse();return {'status':r.status,'headers':dict(r.getheaders()),'body':json.loads(r.read())}
 finally:c.close()
results=[]
for environment,port,keyname in [('test',8993,'PREPARE_CONTROL_TOKEN'),('prod',8994,'PROD_CONTROL_TOKEN')]:
 service='closure-epoch-mcp-component-'+uuid.uuid4().hex[:12];scope={'projectId':project,'resourceKey':'service://'+service+'/'+environment};token=os.environ[keyname]
 seed=request(port,'/control/seed',token,{**scope,'version':'component-baseline','configuration':{'failEvery':0,'delayMs':0}})
 if seed['status']!=200:raise RuntimeError('Preserve existing seed data')
 path='/mcp?'+urllib.parse.urlencode({'projectId':project,'service':service})
 init=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],{'jsonrpc':'2.0','id':uuid.uuid4().hex,'method':'initialize','params':{'protocolVersion':'2024-11-05','clientInfo':{'name':'epoch-component','version':'1'},'capabilities':{}}})
 session=init['headers']['Mcp-Session-Id'];headers={'Mcp-Session-Id':session};rpc_records=[]
 def rpc(method,params):
  ident='epoch-component-'+uuid.uuid4().hex;r=request(8995,path,os.environ['MCP_ACCEPTANCE_TOKEN'],{'jsonrpc':'2.0','id':ident,'method':method,'params':params},headers);rpc_records.append({'id':ident,'method':method,'params':params,'response':r});return ident,r['body']['result']
 _,catalog=rpc('tools/list',{});definition=next(t for t in catalog['tools'] if t['name']==environment+'_apply_configuration')
 _,read=rpc('tools/call',{'name':environment+'_read_state','arguments':{'projectId':project,'service':service}})
 command={'projectId':project,'service':service,'expectedVersion':'component-baseline','expectedEpoch':read['structuredContent']['epoch'],'version':'component-baseline','configuration':{'failEvery':0,'delayMs':1},'executionKey':'epoch-key-'+uuid.uuid4().hex,'deadline':(dt.datetime.now(dt.timezone.utc)+dt.timedelta(seconds=60)).isoformat()}
 valid_id,valid=rpc('tools/call',{'name':environment+'_apply_configuration','arguments':command})
 stale_id,stale=rpc('tools/call',{'name':environment+'_apply_configuration','arguments':{**command,'executionKey':'stale-key-'+uuid.uuid4().hex}})
 lookup_id,lookup=rpc('tools/call',{'name':environment+'_lookup_receipt','arguments':{'projectId':project,'service':service,'executionKey':command['executionKey']}})
 evidence=request(port,'/evidence?'+urllib.parse.urlencode(scope),token)['body'];audit=evidence['audit_request'];lookup_content=lookup['structuredContent']
 checks={'epochAdvertisedAsInteger':definition['inputSchema']['properties']['expectedEpoch']['type']=='integer','realReadBeforeWrite':read['structuredContent']['epoch']==0 and any(a['rpc_id']==rpc_records[1]['id'] and a['path']=='/control/state' and a['http_status']==200 for a in audit),'validEpochOneActualCommit':not valid['isError'] and valid['structuredContent']['epoch']==evidence['state']['epoch']==1 and len(evidence['receipt'])==len(evidence['dispatch'])==1,'staleEpochRejectedAtActualTarget':stale['isError'] and stale['content'][0]['text']=='EPOCH_CONFLICT' and sum(a['rpc_id']==stale_id and a['path']=='/control/apply' and a['http_status']==409 for a in audit)==1,'lookupNativeOutputIdentityComplete':not lookup['isError'] and lookup_content['status']=='FOUND' and all(lookup_content[k]==v for k,v in {**scope,'environment':environment}.items()),'lookupRetainsAuthoritativeReceipt':lookup_content['receipt']==valid['structuredContent'] and json.loads(evidence['receipt'][0]['body_json'])==valid['structuredContent'],'onlyApprovedComponentScopeTouched':all(a['project_id']==project and a['resource_key']==scope['resourceKey'] for a in audit)}
 results.append({'environment':environment,'service':service,'checks':checks,'rpc':rpc_records,'target':evidence})
print(json.dumps(results,ensure_ascii=False))
"""
def main(output):
 if output.exists():raise ValueError('Retain prior evidence')
 d={'scope':'ACTUAL_MCP_HTTP_SQLITE_COMPONENT_PRIVATE_TEST_OPERATOR_ONLY','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'platformApproval':'NOT_ASSERTED','taskAcceptance':'NOT_ASSERTED','newFormalBenchmarkSamples':0,'modelRequests':0,'sourceHashes':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),ROOT/'scripts/fixtures/platform-closure-mcp.py',ROOT/'scripts/fixtures/platform-closure-resource.py']}}
 r=subprocess.run(['docker','exec','-i','orbisops-acceptance-platform-closure-mcp-1','python3','-'],input=PEER,text=True,capture_output=True,timeout=60);d.update(exitCode=r.returncode,completedAt=dt.datetime.now(dt.timezone.utc).isoformat());d['results']=json.loads(r.stdout) if r.returncode==0 else [];d['status']='PASS' if r.returncode==0 and all(all(x['checks'].values()) for x in d['results']) else 'FAIL';d['error']=r.stderr[-2000:];output.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in d.items() if k!='results'},ensure_ascii=False));raise SystemExit(0 if d['status']=='PASS' else 1)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);main(p.parse_args().output)
