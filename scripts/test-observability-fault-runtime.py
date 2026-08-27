#!/usr/bin/env python3
"""Fault/recovery acceptance against only the named isolated business containers.

Stops are always paired with starts in finally blocks; volumes and request facts are retained.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import runpy
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
runtime = runpy.run_path(str(ROOT / 'scripts/test-business-workflow-runtime.py'))
compose = runpy.run_path(str(ROOT / 'scripts/local-acceptance.py'))['compose']
TARGET = 'orbisops-acceptance-workflow-target-1'


def get(url):
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)


def wait_for(fn, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            value = fn()
            if value:
                return value
        except (OSError, ValueError, RuntimeError):
            pass
        time.sleep(1)
    raise AssertionError('Recovery condition not reached within deadline')


def up(value):
    response = get('http://127.0.0.1:19062/api/v1/query?' + urllib.parse.urlencode({'query':'up{job="ops-workflow-target"}'}))
    return response['data']['result'] and float(response['data']['result'][0]['value'][1]) == value


def outbox():
    code = "import sqlite3,json; c=sqlite3.connect('file:/state/target.sqlite?mode=ro',uri=True); print(json.dumps(dict(zip(('total','mysqlPending','esPending'),c.execute('SELECT COUNT(*),COALESCE(SUM(mysql_done=0),0),COALESCE(SUM(es_done=0),0) FROM events').fetchone()))))"
    return json.loads(subprocess.check_output(['docker','exec',TARGET,'python3','-c',code],text=True))


def isolated_container_state(service):
    info = json.loads(subprocess.check_output(['docker', 'inspect', 'orbisops-acceptance-' + service + '-1'], text=True))[0]
    labels = info['Config']['Labels']
    assert labels['com.docker.compose.project'] == 'orbisops-acceptance'
    assert labels['com.docker.compose.service'] == service
    return {'running': info['State']['Running'], 'health': info['State'].get('Health', {}).get('Status'),
            'image': info['Image'], 'volumes': sorted(m['Name'] for m in info['Mounts'] if m['Type'] == 'volume')}


def counters():
    with urllib.request.urlopen('http://127.0.0.1:18262/metrics',timeout=5) as response:
        return sorted(line for line in response.read().decode().splitlines() if line.startswith('ops04_http_requests_total'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--full-window-alert',required=True,help='An already collected 30-minute slow-service alert window')
    args=parser.parse_args()
    if args.output.exists() or args.output.with_suffix('.sql').exists():
        parser.error('Preserve existing evidence; choose a new output path')
    alert = dt.datetime.fromisoformat(args.full_window_alert.replace('Z', '+00:00'))
    if alert.tzinfo is None or alert + dt.timedelta(minutes=15) > dt.datetime.now(dt.timezone.utc):
        parser.error('The alert needs an explicit timezone and a fully elapsed 30-minute window')
    services = ('elasticsearch-acceptance', 'workflow-target')
    before = {name: isolated_container_state(name) for name in services}
    assert all(item['running'] and item['health'] in (None, 'healthy') for item in before.values()), before
    assert get('http://127.0.0.1:19262/_cluster/health')['status'] in ('green', 'yellow')
    assert get('http://127.0.0.1:18262/ready')['status'] == 'ready'
    result={'result':'FAIL','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'checks':{},
            'scope':'REAL_COMPONENTS_AND_CONFIGURED_MODEL_NATURAL_LANGUAGE', 'containersBefore':before,
            'startedRuns':runtime['started_runs']}
    def save():
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        args.output.with_suffix('.sql').write_text('-- Read-only MySQL verification queries; no outcome writes.\n'
                                                  + '\n'.join(runtime['sql_queries']) + '\n')
    save()
    try:
        wait_for(lambda: outbox()['esPending'] == 0)
        traces=[]
        try:
            compose('--profile','business','stop','elasticsearch-acceptance')
            for _ in range(5):
                with urllib.request.urlopen('http://127.0.0.1:18262/orders/ops-acc-a-order-1',timeout=5) as response:
                    traces.append(response.headers['X-Trace-Id'])
                    assert json.load(response)['order']['orderId']=='ops-acc-a-order-1'
            wait_for(lambda: outbox()['mysqlPending']==0)
            result['duringEsOutage']=outbox()
            result['outageRequestTraceIds']=traces
            assert result['duringEsOutage']['esPending'] >= 5
            save()
            run=runtime['run_case']('ops-acc-a-service-4',args.full_window_alert,'INCONCLUSIVE')
            result['esUnavailableWorkflow']=run
            assert run['result']=='PASS' and 'logs_window:UNAVAILABLE' in run['report']['evidenceGaps']
            assert 'sql' not in run['executedNodes'] and 'end' in run['executedNodes']
            result['checks']['missingLogsDoNotProduceHealthyOrRootCause']=True
            save()
        finally:
            compose('--profile','business','start','elasticsearch-acceptance')
        wait_for(lambda: get('http://127.0.0.1:19262/_cluster/health')['status'] in ('green','yellow'),120)
        wait_for(lambda: outbox()['esPending']==0 and outbox()['mysqlPending']==0)
        replay=[]
        for trace in traces:
            es=get('http://127.0.0.1:19262/ops04-orders/_doc/'+trace)['_source']
            rows=runtime['rows']("SELECT JSON_OBJECT('count',COUNT(*),'status',MIN(http_status),'duration',MIN(duration_ms)) FROM ops_acceptance_business_a.ops04_request WHERE event_id="+runtime['quoted'](trace))
            assert rows[0]['count']==1 and rows[0]['status']==es['http_status'] and rows[0]['duration']==es['duration_ms']
            replay.append(trace)
        result['checks']['outboxReplayedWithoutDuplicateOrChangedRequestFacts']=replay
        recovered=runtime['run_case']('ops-acc-a-service-4',args.full_window_alert,'OBSERVED_ANOMALY')
        assert recovered['result']=='PASS'
        result['esRecoveredWorkflow']=recovered
        result['beforeTargetRestart']={'outbox':outbox(),'counters':counters()}
        save()
        try:
            compose('--profile','business','stop','workflow-target')
            wait_for(lambda: up(0),30)
            run=runtime['run_case']('ops-acc-a-service-3',dt.datetime.now(dt.timezone.utc).isoformat(),'UNREACHABLE')
            result['targetUnavailableWorkflow']=run
            assert run['result']=='PASS' and run['report']['metrics']['reachability']=='UNREACHABLE'
            assert run['report']['metrics']['sampleCountLowerBound']<100
            result['checks']['UpZeroIndependentOfLowTraffic']=True
            save()
        finally:
            compose('--profile','business','start','workflow-target')
        wait_for(lambda: get('http://127.0.0.1:18262/ready')['status']=='ready')
        wait_for(lambda: up(1),30)
        wait_for(lambda: isolated_container_state('workflow-target')['health'] == 'healthy', 40)
        result['afterTargetRestart']={'outbox':outbox(),'counters':counters()}
        assert result['beforeTargetRestart']==result['afterTargetRestart'], 'Durable request counters changed across restart without new traffic'
        result['checks']['targetCounterAndOutboxSurviveRestart']=True
        result['containersAfter']={name: isolated_container_state(name) for name in services}
        assert before == result['containersAfter'], 'Container image, volume or health changed across recovery'
        result['checks']['sameImagesAndVolumesHealthyAfterRecovery']=True
        result['result']='PASS'
    except Exception as error:
        result['error']=type(error).__name__+': '+str(error)
        raise
    finally:
        result['containersFinal']={name: isolated_container_state(name) for name in services}
        result['finishedAt']=dt.datetime.now(dt.timezone.utc).isoformat()
        save()
    print(json.dumps({'result':result['result'],'checks':result['checks']},ensure_ascii=False))


if __name__=='__main__':
    main()
