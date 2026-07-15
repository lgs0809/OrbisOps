#!/usr/bin/env python3
"""Cross-check an actual observation run with read-only MySQL and independent SQLite.

This proves the target/interval and every actual request, never manufactures task
acceptance or claims that the external world remained unchanged.
"""
import argparse
import datetime as dt
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-closure-business-a'

def inspect(run,service,output):
    if output.exists():raise ValueError('Prior evidence is retained')
    h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));q=h['quoted'];statements=[]
    def rows(query):statements.append(query+';');return h['rows'](query)
    fact=rows("SELECT JSON_OBJECT('runId',run_id,'status',status,'error',error_message,'projectId',project_id,'sessionId',session_id,'userId',user_id,'hash',agent_definition_hash,'request',request_json) FROM ai_ops_agent_run WHERE run_id="+q(run))[0]
    if fact['projectId']!=PROJECT:raise ValueError('Exact isolated business-source project required')
    receipts=rows("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'output',full_output,'status',status) FROM ai_ops_tool_result WHERE run_id="+q(run)+" AND source='MCP_REMOTE_TOOL' ORDER BY id")
    calls=rows("SELECT JSON_OBJECT('tool',tool_name,'readOnly',read_only,'status',status,'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id="+q(run)+' ORDER BY id')
    dispatches=rows("SELECT JSON_OBJECT('rpcId',request_id,'tool',tool_name,'attempt',physical_attempt) FROM ai_ops_workflow_tool_dispatch WHERE run_id="+q(run)+' ORDER BY id')
    events=h['api']('/api/v1/admin/ops-agent-runs/'+run+'/events/list',token=h['token'])
    normalized=[json.loads(r['output']).get('normalizedContent') for r in receipts if r['status']=='SUCCEEDED']
    observations=[n for n in normalized if n and 'beforeState' in n and 'afterState' in n]
    if len(observations)!=1:raise ValueError('Exactly one actual interval receipt required')
    observed=observations[0];environment=observed['environment'];resource='service://'+service+'/'+environment
    source="""import json,sqlite3,sys
resource,project=sys.argv[1:3]
with sqlite3.connect('file:/state/resource.sqlite?mode=ro',uri=True) as c:
 c.row_factory=sqlite3.Row
 print(json.dumps({t:[dict(r) for r in c.execute('SELECT * FROM '+t+' WHERE resource_key=?',(resource,))] for t in ['resource','request','receipt','dispatch','audit_request']}))
"""
    target=json.loads(subprocess.check_output(['docker','exec','-i','orbisops-acceptance-platform-closure-'+environment+'-1','python3','-',resource,PROJECT],input=source,text=True))
    peer_source="""import sqlite3,json,sys
with sqlite3.connect('file:/state/closure-mcp.sqlite?mode=ro',uri=True) as c:
 c.row_factory=sqlite3.Row
 ids,hashes,project,service=json.loads(sys.argv[1]),json.loads(sys.argv[2]),sys.argv[3],sys.argv[4]
 if ids:
  query='SELECT * FROM calls WHERE rpc_id IN ('+','.join('?' for _ in ids)+') ORDER BY id'; args=ids
 else:
  query='SELECT * FROM calls WHERE project_id=? AND service=? AND result_hash IN ('+','.join('?' for _ in hashes)+') ORDER BY id'; args=[project,service]+hashes
 print(json.dumps([dict(r) for r in c.execute(query,args)]))
"""
    ids=[d['rpcId'] for d in dispatches]
    native_result_hashes=[]
    for receipt in receipts:
        envelope=json.loads(receipt['output'])
        native={key:envelope[key] for key in ['isError','content','structuredContent'] if key in envelope}
        native_result_hashes.append(hashlib.sha256(json.dumps(native,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest())
    peer=json.loads(subprocess.check_output(['docker','exec','-i','orbisops-acceptance-platform-closure-mcp-1','python3','-',json.dumps(ids),json.dumps(native_result_hashes),PROJECT,service],input=peer_source,text=True))
    if not ids:
        ids=[p['rpc_id'] for p in peer]
    actual={row['trace_id']:row for row in target['request']};requested=observed['requests']
    start=dt.datetime.fromisoformat(observed['observationStart']).timestamp();end=dt.datetime.fromisoformat(observed['observationEnd']).timestamp()
    before,after=observed['beforeState'],observed['afterState']
    models=[e['payload'] for e in events if e['eventType']=='MODEL_RESPONSE_VERIFIED']
    orders_audit=[row for row in target['audit_request'] if row['rpc_id'] in ids
        and row['path']=='/orders' and start<=row['received_at']<=end]
    checks={'actualRunSucceeded':fact['status']=='SUCCEEDED','twoReadOnlyPhysicalCalls':len(calls)==len(peer)==2 and (not dispatches or len(dispatches)==2) and [c['tool'] for c in calls]==[p['tool'] for p in peer]==['test_read_state','test_verify_observation'] and len(set(ids))==2 and all(c['readOnly']==1 and c['status']=='SUCCEEDED' for c in calls),
        'peerReceiptHashesMatchNativeResults':len(peer)==len(native_result_hashes) and [p['result_hash'] for p in peer]==native_result_hashes,
        'peerIdentity':all(p['project_id']==PROJECT and p['service']==service and p['status']=='COMPLETED' for p in peer),
        'scopeIdentity':all(v['projectId']==PROJECT and v['resourceKey']==resource and v['environment']==environment for v in [observed,before,after]),
        'configurationDigestRecomputes':all(hashlib.sha256(json.dumps(v['configuration'],ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()==v['configurationDigest'] for v in [before,after]),
        'versionEpochConfigurationUnchangedInInterval':observed['configurationUnchanged'] and all(before[k]==after[k] for k in ['version','epoch','configurationDigest']),
        'twentyUniqueSuccessfulActualOrders':len(requested)==len({r['traceId'] for r in requested})==observed['requestCount']==20 and observed['errorCount']==0 and all(r['httpStatus']==200 and r['traceId'] in actual and actual[r['traceId']]['status']==r['httpStatus'] and actual[r['traceId']]['version']==r['version']==before['version'] and start<=actual[r['traceId']]['observed_at']<=end for r in requested),
        'actualOrderMethodAndPath':len(orders_audit)==20 and all(row['method']=='GET' and row['http_status']==200 for row in orders_audit),
        'targetNoConfigurationWrite':not target['dispatch'] and not target['receipt'] and len(target['resource'])==1 and target['resource'][0]['epoch']==0,
        'receiptHashes':all(hashlib.sha256(r['output'].encode()).hexdigest()==r['hash'] for r in receipts),
        'modelIdentity':bool(models) and all(m['requestedModel']==m['responseModel'] and m['responseModel'] in ['gpt-5.6-luna','gpt-5.6-terra'] for m in models),
        'naturalLanguageInput':not json.loads(fact['request'])['query'].lstrip().startswith(('{','['))}
    if 'allRequestsSucceeded' in observed:
        checks['recomputableRequestAggregate']=observed['allRequestsSucceeded']==all(r['httpStatus']==200 for r in requested) and observed['errorCount']==sum(r['httpStatus']!=200 for r in requested)
        checks['exposedRequestIdentityMatchesActualAudit']=observed['requestMethod']=='GET' and observed['routeDefinition']=='/orders' and all(r['method']=='GET' and r['path']=='/orders' for r in requested)
    if 'minimumRequestDurationMs' in observed:
        checks['nativeRequestDurationsMatchEveryPersistedTrace'] = all(
            r['durationMs']==actual[r['traceId']]['duration_ms'] for r in requested)
        checks['durationAggregatesRecompute'] = observed['minimumRequestDurationMs']==min(
            r['durationMs'] for r in requested) and Decimal(str(observed['totalRequestDurationMs'])).quantize(Decimal('0.001'))==sum(
            (Decimal(str(r['durationMs'])) for r in requested), Decimal(0)).quantize(Decimal('0.001'))
    configured_delay=before['configuration']['delayMs']
    checks['actualDelayConditionMatchesConfiguration']=configured_delay==after['configuration']['delayMs'] and all(actual[r['traceId']]['duration_ms']>=configured_delay for r in requested) and (end-start)*1000>=configured_delay*len(requested)
    report={'status':'PASS' if all(checks.values()) else 'FAIL','recordedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'scope':'ACTUAL_LOCAL_TARGET_OBSERVATION_NOT_TASK_ACCEPTANCE_NOT_FORMAL_BENCHMARK',
        'checks':checks,'actualDelayCondition':{'configuredDelayMs':configured_delay,'minimumActualRequestDurationMs':min(actual[r['traceId']]['duration_ms'] for r in requested),'actualObservationDurationMs':(end-start)*1000},'run':fact,'receipts':receipts,'calls':calls,'dispatches':dispatches,'peerCalls':peer,'target':target,'modelIdentities':models,
        'physicalTraceBinding':'WORKFLOW_DISPATCH_RPC_IDS' if dispatches else 'EXACT_UNIQUE_NATIVE_MCP_RESULT_HASHES',
        'modelIdentityEvidence':'DURABLE_RESPONSE_VERIFIED' if models else 'NOT_PROVEN_BY_AVAILABLE_DURABLE_EVENTS',
        'deployedJarSha256':subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0],
        'sourceHashes':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['scripts/fixtures/platform-closure-mcp.py','scripts/fixtures/platform-closure-resource.py','scripts/inspect-closure-observation.py']}}
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');output.with_suffix('.sql').write_text('\n'.join(statements)+'\n');output.with_name(output.stem+'-events.json').write_text(json.dumps(events,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'checks':checks}));return report

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',required=True);parser.add_argument('--service',required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();r=inspect(args.run,args.service,args.output);raise SystemExit(0 if r['status']=='PASS' else 1)
