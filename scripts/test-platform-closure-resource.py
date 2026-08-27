#!/usr/bin/env python3
"""Actual scoped HTTP/SQLite fault and persistence checks, not Orbis task acceptance.

Only the two named closure containers are restarted. Existing resource rows,
application databases, volumes and prior evidence are retained.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PEER = r'''
import concurrent.futures, datetime as dt, hashlib, http.client, json, os, sys, time, urllib.parse, uuid
environment, phase = sys.argv[1:3]
port = 8993 if environment == 'test' else 8994
other = 8994 if environment == 'test' else 8993
token = os.environ['CLOSURE_CONTROL_TOKEN']
def request(path, body=None, *, scope=None, auth=True, target=port):
    connection = http.client.HTTPConnection('127.0.0.1', target, timeout=20)
    headers = {'Content-Type':'application/json', 'X-Orbis-Rpc-Id':'component-'+uuid.uuid4().hex}
    if auth: headers['Authorization']='Bearer '+token
    if scope: path += '?'+urllib.parse.urlencode(scope)
    try:
        connection.request('POST' if body is not None else 'GET', path, json.dumps(body) if body is not None else None, headers)
        response = connection.getresponse()
        return {'status':response.status, 'body':json.loads(response.read())}
    except (http.client.RemoteDisconnected, ConnectionResetError) as failure:
        return {'status':'CONNECTION_DROPPED', 'failureType':type(failure).__name__}
    finally: connection.close()
def state(scope): return request('/control/state', scope=scope)['body']
def evidence(scope): return request('/evidence', scope=scope)['body']
def deadline(seconds=300): return (dt.datetime.now(dt.timezone.utc)+dt.timedelta(seconds=seconds)).isoformat()
def command(scope, key, before, after):
    return {**scope, 'expectedVersion':before,'version':after,'configuration':{'failEvery':0,'delayMs':0},'executionKey':key,'deadline':deadline()}
def fault(scope, profile): return request('/control/fault', {**scope,'profile':profile})
checks={}; records={}
if phase == 'create':
    suffix=uuid.uuid4().hex
    def seed(label):
        scope={'projectId':'ops-platform-closure-dev','resourceKey':'service://closure-component-'+suffix+'-'+label+'/'+environment}
        body={**scope,'version':'before-'+label,'configuration':{'failEvery':0,'delayMs':0}}
        first=request('/control/seed',body)
        second=request('/control/seed',body)
        conflict=request('/control/seed',{**body,'version':'overwrite-disallowed'})
        checks[label+'SeedPreservesExisting']=first['status']==second['status']==200 and conflict['status']==409 and state(scope)['version']==body['version']
        return scope
    concurrent_scope=seed('concurrent')
    change=command(concurrent_scope,'apply-'+suffix,'before-concurrent','after-concurrent')
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        replies=list(pool.map(lambda _:request('/control/apply',change),range(8)))
    actual=evidence(concurrent_scope)
    checks['eightConcurrentSameCommandOneDurableChange']=all(r['status']==200 for r in replies) and len({json.dumps(r['body'],sort_keys=True) for r in replies})==1 and actual['state']['epoch']==1 and len(actual['receipt'])==len(actual['dispatch'])==1
    rejected=[request('/control/apply',{**change,'version':'conflicting-body'}),request('/control/apply',command(concurrent_scope,'stale-'+suffix,'before-concurrent','stale-write')),request('/control/apply',{**command(concurrent_scope,'expired-'+suffix,'after-concurrent','expired-write'),'deadline':deadline(-1)}),request('/control/state',scope={**concurrent_scope,'projectId':'ops-platform-closure-holdout'}),request('/control/state',scope=concurrent_scope,auth=False),request('/control/state',scope=concurrent_scope,target=other)]
    checks['identityStaleExpiredIdempotencyAndCredentialRejectWithoutWrite']=[r['status'] for r in rejected]==[409,409,409,404,403,403] and evidence(concurrent_scope)['state']['epoch']==1 and len(evidence(concurrent_scope)['receipt'])==1
    epoch_scope=seed('epoch')
    same_version={**command(epoch_scope,'same-version-'+suffix,'before-epoch','before-epoch'),'expectedEpoch':0,'configuration':{'failEvery':0,'delayMs':1}}
    same_applied=request('/control/apply',same_version)
    stale_epoch=request('/control/apply',{**same_version,'executionKey':'stale-epoch-'+suffix})
    checks['sameVersionChangedConfigurationRejectsStaleEpoch']=same_applied['status']==200 and stale_epoch['status']==409 and stale_epoch['body']['error']=='EPOCH_CONFLICT' and evidence(epoch_scope)['state']['epoch']==1 and len(evidence(epoch_scope)['receipt'])==1
    advance={**command(epoch_scope,'epoch-advance-'+suffix,'before-epoch','intermediate-epoch'),'expectedEpoch':1}
    restore={**command(epoch_scope,'epoch-restore-'+suffix,'intermediate-epoch','before-epoch'),'expectedEpoch':2}
    advanced=request('/control/apply',advance);restored=request('/control/apply',restore)
    stale_after_aba=request('/control/apply',{**same_version,'executionKey':'aba-stale-'+suffix,'expectedEpoch':1})
    checks['versionAbaDoesNotRestoreOldEpochAuthority']=advanced['status']==restored['status']==200 and stale_after_aba['status']==409 and stale_after_aba['body']['error']=='EPOCH_CONFLICT' and evidence(epoch_scope)['state']['epoch']==3
    contenders=[{**command(epoch_scope,'epoch-contender-'+suffix+'-'+str(i),'before-epoch','before-epoch'),'expectedEpoch':3,'configuration':{'failEvery':0,'delayMs':i}} for i in range(2)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:epoch_replies=list(pool.map(lambda c:request('/control/apply',c),contenders))
    epoch_actual=evidence(epoch_scope)
    checks['concurrentSameVersionEpochCasOneCommit']=sorted(r['status'] for r in epoch_replies)==[200,409] and next(r['body']['error'] for r in epoch_replies if r['status']==409)=='EPOCH_CONFLICT' and epoch_actual['state']['epoch']==4 and len(epoch_actual['receipt'])==len(epoch_actual['dispatch'])==4
    malformed=[{'failEvery':-1,'delayMs':0},{'failEvery':0,'delayMs':501},{'failEvery':False,'delayMs':0},{'failEvery':0,'delayMs':0.5},{'failEvery':0},{'failEvery':0,'delayMs':0,'extra':True}]
    invalid_config=[request('/control/apply',{**command(epoch_scope,'invalid-'+suffix+'-'+str(i),'before-epoch','poison'),'expectedEpoch':4,'configuration':cfg}) for i,cfg in enumerate(malformed)]
    invalid_epoch=[request('/control/apply',{**command(epoch_scope,'invalid-epoch-'+suffix+'-'+str(i),'before-epoch','poison'),'expectedEpoch':v}) for i,v in enumerate([-1,True,4.0,'4'])]
    checks['invalidConfigurationAndEpochRejectBeforeAnyCommit']=all(r['status']==400 for r in invalid_config+invalid_epoch) and evidence(epoch_scope)['state']['epoch']==4 and len(evidence(epoch_scope)['receipt'])==len(evidence(epoch_scope)['dispatch'])==4
    conflict_epoch=request('/control/apply',{**same_version,'expectedEpoch':4})
    same_epoch_replay=request('/control/apply',same_version)
    checks['immutableExecutionKeyIncludesDeclaredEpoch']=conflict_epoch['status']==409 and conflict_epoch['body']['error']=='IDEMPOTENCY_CONFLICT' and same_epoch_replay['status']==200 and same_epoch_replay['body']==same_applied['body'] and evidence(epoch_scope)['state']['epoch']==4
    uncertain=seed('uncertain')
    fault(uncertain,{'beforeCommit':True})
    change2=command(uncertain,'unknown-'+suffix,'before-uncertain','after-uncertain')
    before_failure=request('/control/apply',change2); before_ledger=evidence(uncertain)
    checks['beforeCommitFailureNoChange']=before_failure['status']==503 and before_ledger['state']['epoch']==0 and not before_ledger['receipt'] and not before_ledger['dispatch']
    fault(uncertain,{'dropAfterCommit':True})
    dropped=request('/control/apply',change2)
    lookup=request('/control/receipt',scope={**uncertain,'executionKey':change2['executionKey']})
    after_ledger=evidence(uncertain)
    checks['unknownCommitResolvedByReadWithoutSecondDispatch']=dropped['status']=='CONNECTION_DROPPED' and lookup['status']==200 and lookup['body']['status']=='FOUND' and after_ledger['state']['epoch']==1 and len(after_ledger['receipt'])==len(after_ledger['dispatch'])==1
    fault(uncertain,{})
    replay=request('/control/apply',change2)
    checks['knownReceiptImmutable']=replay['status']==200 and replay['body']==lookup['body']['receipt'] and evidence(uncertain)['state']['epoch']==1 and len(evidence(uncertain)['dispatch'])==1
    recovery=seed('recovery')
    fault(recovery,{'postVersion':'unhealthy-recovery','failEvery':2})
    deploy=command(recovery,'deploy-'+suffix,'before-recovery','unhealthy-recovery')
    deployed=request('/control/apply',deploy)
    observed=[request('/orders',scope=recovery) for _ in range(20)]
    checks['actualPostDeployHttpFailureDetected']=deployed['status']==200 and sum(r['status']==503 for r in observed)==10 and len({r['body']['traceId'] for r in observed})==20
    rollback=command(recovery,'rollback-'+suffix,'unhealthy-recovery','before-recovery')
    rolled=request('/control/apply',rollback)
    rechecked=[request('/orders',scope=recovery) for _ in range(20)]
    ledger=evidence(recovery)
    checks['actualRollbackAndHealthyRecheck']=rolled['status']==200 and all(r['status']==200 for r in rechecked) and ledger['state']['epoch']==2 and len(ledger['receipt'])==len(ledger['dispatch'])==2 and len(ledger['request'])==40 and sum(row['status']==503 for row in ledger['request'])==10
    records={label:evidence(scope) for label,scope in [('concurrent',concurrent_scope),('epoch',epoch_scope),('uncertain',uncertain),('recovery',recovery)]}
    checks['allPhysicalRequestsAudited']=all(row['rpc_id'].startswith('component-') for record in records.values() for row in record['audit_request']) and any(row['http_status'] is None and row['path']=='/control/apply' for row in records['uncertain']['audit_request'])
elif phase == 'verify':
    previous=json.load(sys.stdin)
    for label,record in previous['records'].items():
        scope={k:record['state'][k] for k in ['projectId','resourceKey']}
        current=evidence(scope); records[label]=current
        checks[label+'StateAndReceiptsSurviveRestart']=all(current['state'][key]==record['state'][key] for key in ['projectId','environment','resourceKey','version','epoch','configuration']) and current['receipt']==record['receipt'] and current['dispatch']==record['dispatch'] and current['request']==record['request']
else: raise ValueError(phase)
print(json.dumps({'status':'PASS' if all(checks.values()) else 'FAIL','environment':environment,'phase':phase,'checks':checks,'records':records,'modelRequests':0,'platformTaskAcceptance':'NOT_ASSERTED','platformApproval':'NOT_ASSERTED'},ensure_ascii=False))
'''

def probe(environment, phase, previous=None):
    container='orbisops-acceptance-platform-closure-'+environment+'-1'
    source=PEER
    if previous is not None:
        # The prior public ledger contains no credential and is injected as data.
        source=source.replace('previous=json.load(sys.stdin)','previous=json.loads('+repr(json.dumps(previous))+')')
    process=subprocess.run(['docker','exec','-i',container,'python3','-',environment,phase],input=source,text=True,capture_output=True,timeout=100)
    if process.returncode: raise RuntimeError(process.stderr[-2500:])
    return json.loads(process.stdout)

def main(output):
    if output.exists(): raise ValueError('Prior evidence must be retained')
    started=dt.datetime.now(dt.timezone.utc).isoformat()
    results=[probe(env,'create') for env in ['test','prod']]
    containers=['orbisops-acceptance-platform-closure-'+env+'-1' for env in ['test','prod']]
    restart=subprocess.run(['docker','restart',*containers],capture_output=True,text=True,timeout=60)
    if restart.returncode: raise RuntimeError('Own resource restart failed')
    # Docker restart completes before the HTTP application is listening.
    import time
    for attempt in range(20):
        try: persistence=[probe(env,'verify',result) for env,result in zip(['test','prod'],results)]; break
        except (RuntimeError,ConnectionError):
            if attempt==19: raise
            time.sleep(.5)
    source=ROOT/'scripts/fixtures/platform-closure-resource.py'
    result={'status':'PASS' if all(r['status']=='PASS' for r in results+persistence) else 'FAIL',
        'startedAt':started,'completedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
        'scope':'ACTUAL_ISOLATED_HTTP_SQLITE_COMPONENT_ONLY','resourceSourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'runnerSourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'results':results+persistence,'modelRequests':0,'formalBenchmarkCases':0,
        'platformApproval':'NOT_ASSERTED','platformTaskAcceptance':'NOT_ASSERTED','restartedContainers':containers}
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='results'},ensure_ascii=False))
    if result['status']!='PASS': raise SystemExit(1)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    main(parser.parse_args().output)
