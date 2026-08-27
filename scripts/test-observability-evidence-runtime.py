#!/usr/bin/env python3
"""Actual adapter HTTP/SQLite evidence, separate from model and TaskAcceptance runs."""
import argparse
import json
from pathlib import Path
import subprocess

CONTAINER_SOURCE = r'''
import os,json,urllib.request,uuid,sqlite3,hashlib,datetime as dt
headers={'Authorization':'Bearer '+os.environ['OBSERVABILITY_MCP_TOKEN'],'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
def call(method,params,identity):
 with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8281/mcp',json.dumps({'jsonrpc':'2.0','id':identity,'method':method,'params':params}).encode(),headers),timeout=20) as r:return json.load(r),r.headers
v,h=call('initialize',{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'actual-read-boundary-evidence','version':'3'}},'init-'+uuid.uuid4().hex);headers['Mcp-Session-Id']=h['Mcp-Session-Id']
a={'projectId':'ops-acceptance-a','serviceId':'ops-acc-a-service-2','environment':'prod','startEpoch':1790999027,'endEpoch':1790999927};b={**a,'startEpoch':1790998122,'endEpoch':1790999022};records=[]
for tool,args in [('change_package_evidence',{'window':a}),('sql_window',{'window':a,'comparisonWindow':b}),('change_package_evidence',{'window':{**a,'serviceId':'ops-acc-a-service-1'}}),('change_package_evidence',{'window':{**a,'projectId':'ops-acceptance-b'}})]:
 identity='native-component-'+uuid.uuid4().hex;value,_=call('tools/call',{'name':tool,'arguments':args},identity);records.append({'rpcId':identity,'tool':tool,'args':args,'response':value})
conn=sqlite3.connect('/state/observability.sqlite');conn.row_factory=sqlite3.Row
native=[dict(x) for x in conn.execute('SELECT * FROM native_responses WHERE rpc_id=?',(records[0]['rpcId'],))];archives=[{k:x[k] for k in ['native_read_id','rpc_id','path','http_status','response_hash','received_at']}|{'actualBytes':len(x['response_json'].encode()),'hashMatches':hashlib.sha256(x['response_json'].encode()).hexdigest()==x['response_hash']} for x in native]
counts={r['rpcId']:{'queries':conn.execute('SELECT COUNT(*) FROM queries WHERE rpc_id=?',(r['rpcId'],)).fetchone()[0],'nativeResponses':conn.execute('SELECT COUNT(*) FROM native_responses WHERE rpc_id=?',(r['rpcId'],)).fetchone()[0]} for r in records}
n=records[0]['response']['result']['structuredContent'];s=records[1]['response']['result']['structuredContent'];checks={'bothActualReadsAvailable':n['status']==s['status']=='AVAILABLE','fourUnclippedNativeResponses':len(archives)==4 and all(x['hashMatches'] for x in archives),'archiveIdentityMatchesMcp':all(x['native_read_id']==n['nativeReadArchiveId'] for x in archives),'physicalReadCorrelation':counts[records[0]['rpcId']]=={'queries':1,'nativeResponses':4} and counts[records[1]['rpcId']]=={'queries':1,'nativeResponses':0},'wrongProjectAndServiceRejected':all(x['response']['result']['isError'] for x in records[2:]),'deniedScopesZeroQueriesAndNativeHttp':all(counts[x['rpcId']]=={'queries':0,'nativeResponses':0} for x in records[2:]),'fullLedgerActualCount':s['comparison']['before']['sampleCount']==895 and s['comparison']['after']['sampleCount']==905,'noInventedSloSuccess':'sloPassed' not in s and 'finalSloVerdict' not in n}
print(json.dumps({'status':'PASS' if all(checks.values()) else 'FAIL','actualExecutedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'scope':'ACTUAL_MCP_ADAPTER_COMPONENT_NOT_LLM_OR_TASK_ACCEPTANCE','checks':checks,'records':records,'nativeArchiveMetadata':archives,'queryAuditAndNativeResponseCounts':counts},ensure_ascii=False,indent=2))
'''


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if a.output.exists(): p.error('Retain previous evidence')
    result=subprocess.run(['docker','exec','-i','-w','/opt/observability-mcp',
        'orbisops-acceptance-observability-mcp-1','python3','-'],input=CONTAINER_SOURCE,
        text=True,capture_output=True,timeout=100)
    a.output.with_suffix('.log').write_text(result.stderr)
    if result.returncode:
        report={'status':'FAIL_HARNESS_OR_COMPONENT','exitCode':result.returncode,
                'stdout':result.stdout,'stderrLog':str(a.output.with_suffix('.log'))}
    else: report=json.loads(result.stdout)
    a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('status','checks') if k in report}))
    if report['status']!='PASS': raise SystemExit(1)
