#!/usr/bin/env python3
"""Idempotently initialize new phase-spec resources without executing a benchmark.

Only facts are read. Native seed conflicts preserve existing resources; no task,
approval, journal, acceptance, fault, or expected outcome is written. Private
control HTTP traffic here never counts as a platform/model task execution.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def prepare(corpus, output):
    if output.exists():
        raise ValueError('Use a new evidence path; retain prior observations')
    manifest = json.loads((corpus/'manifest.json').read_text())
    facts_bytes = (corpus/'facts.json').read_bytes()
    if hashlib.sha256(facts_bytes).hexdigest() != manifest['factsSha256']:
        raise ValueError('Frozen facts changed')
    facts = json.loads(facts_bytes)
    report = {'status': 'RUNNING_RESOURCE_SETUP_ONLY', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'factsSha256': manifest['factsSha256'], 'referenceSha256': manifest['referenceSha256'],
        'referenceContentsRead': False, 'evaluatedTasks': 0, 'formalAttempts': 0,
        'privateSetupNeverCountsAsModelPhysicalToolExecution': True, 'environments': []}
    remote = '''import http.client,json,os,sys,hashlib
payload=json.load(sys.stdin);environment=payload['environment'];results=[]
def call(method,path,body):
 connection=http.client.HTTPConnection('127.0.0.1',8993 if environment=='test' else 8994,timeout=15)
 try:
  headers={'Authorization':'Bearer '+os.environ['CLOSURE_CONTROL_TOKEN']}
  if body is not None:headers['Content-Type']='application/json'
  connection.request(method,path,body=json.dumps(body) if body is not None else None,headers=headers)
  response=connection.getresponse();raw=response.read();return response.status,json.loads(raw)
 finally:connection.close()
for case in payload['facts']:
 ref=case['resourceRefs'][environment];body={'projectId':case['projectId'],'resourceKey':ref['resourceKey'],
 'version':case['initialState']['version'],'configuration':case['initialState']['configuration']}
 code,state=call('POST','/control/seed',body)
 import urllib.parse
 readCode,read=call('GET','/control/state?'+urllib.parse.urlencode({'projectId':body['projectId'],'resourceKey':body['resourceKey']}),None)
 results.append({'caseId':case['caseId'],'seedHttpStatus':code,'seedResponse':state,'readHttpStatus':readCode,'actualState':read,
 'existingStateNeverReset':True,'exactInitialState':readCode==200 and read.get('projectId')==case['projectId']
 and read.get('resourceKey')==ref['resourceKey'] and read.get('version')==case['initialState']['version']
 and read.get('configuration')==case['initialState']['configuration'] and read.get('epoch')==0})
print(json.dumps(results,ensure_ascii=False))
'''
    try:
        for environment in ['test', 'prod']:
            container = 'orbisops-acceptance-platform-closure-'+environment+'-1'
            # Code is passed as argv; facts through stdin. The control credential remains inside the container.
            command = ['docker', 'exec', '-i', container, 'python3', '-c', remote]
            actual = subprocess.run(command, input=json.dumps({'environment': environment, 'facts': facts}),
                text=True, capture_output=True, check=True, timeout=180)
            results = json.loads(actual.stdout)
            inspected = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
            report['environments'].append({'environment': environment, 'container': container,
                'containerId': inspected['Id'], 'image': inspected['Image'], 'mounts': inspected['Mounts'],
                'resourceFixtureSha256': hashlib.sha256((ROOT/'scripts/fixtures/platform-closure-resource.py').read_bytes()).hexdigest(),
                'results': results, 'noReferenceMounted': not any('platform-phase-spec' in m['Source'] for m in inspected['Mounts'])})
        checks = {'exact120ResourceIdentities': sum(len(e['results']) for e in report['environments']) == 120,
            'actualSeedAndRead': all(r['seedHttpStatus'] == r['readHttpStatus'] == 200 and r['exactInitialState']
                for e in report['environments'] for r in e['results']),
            'referenceNotMounted': all(e['noReferenceMounted'] for e in report['environments'])}
        report.update(checks=checks, status='PASS_RESOURCE_INITIALIZATION_ONLY' if all(checks.values()) else 'FAIL_RESOURCE_SETUP')
    except Exception as error:
        report.update(status='FAIL_RESOURCE_SETUP', error=type(error).__name__+': '+str(error))
        raise
    finally:
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': report['status'], 'resources': 120, 'evaluatedTasks': 0, 'checks': checks}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.corpus, args.output)
