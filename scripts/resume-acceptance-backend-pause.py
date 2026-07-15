#!/usr/bin/env python3
"""Restore only the owned backend/namespace peers from a retained local pause receipt.

Uses the normal Compose startup settings. It never changes business queues,
retry clocks, model identities, images, volumes, or approvals. Native workers
may progress after startup, and that progress is recorded rather than frozen.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main(receipt, output):
    if output.exists():
        raise ValueError('Keep earlier restore evidence')
    paused = json.loads(receipt.read_text())
    if paused.get('status') != 'PASS_CONTROLLED_LOCAL_PAUSE' or not paused.get('containersImagesMountsPreserved'):
        raise ValueError('A completed owned pause receipt is required')
    lifecycle = runpy.run_path(str(ROOT/'scripts/backend-namespace-lifecycle.py'))
    deployment = runpy.run_path(str(ROOT/'scripts/deploy-tested-acceptance.py'))
    compose = runpy.run_path(str(ROOT/'scripts/local-acceptance.py'))['compose']
    before = paused['before']
    backend = lifecycle['inspect'](lifecycle['BACKEND'])
    if backend['running'] or backend['labels'].get('com.docker.compose.project') != lifecycle['PROJECT']:
        raise ValueError('Only the retained stopped acceptance backend is supported')
    old = before['backend']
    if any(backend[k] != old[k] for k in ('id', 'image', 'mounts')):
        raise ValueError('The paused backend identity changed; retained')
    for peer in before['peers']:
        item = lifecycle['inspect'](peer['name'])
        if item['running'] or item['labels'].get('com.docker.compose.project') != lifecycle['PROJECT'] or any(
                item[k] != peer[k] for k in ('id', 'image', 'mounts')):
            raise ValueError('The paused peer identity changed; retained')
    report = {'status': 'RESTORING_OWNED_LOCAL_STACK', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
              'pauseReceipt': str(receipt), 'pauseSha256': hashlib.sha256(receipt.read_bytes()).hexdigest(),
              'idleAdmission': deployment['require_idle_runs'](), 'businessQueueMutations': 0,
              'composeSha256': hashlib.sha256((ROOT/'deploy/compose.acceptance.yml').read_bytes()).hexdigest()}

    def save():
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')

    save()
    compose('up', '-d', '--no-deps', '--no-build', 'backend')
    lifecycle['wait_backend']()
    services = [p['service'] for p in before['peers']]
    if services:
        compose(*lifecycle['PROFILES'], 'up', '-d', '--no-deps', '--no-build', '--force-recreate', *services)
    current, peers = lifecycle['running_peers']()
    actual = {p['service']: p for p in peers}
    expected_namespace = lifecycle['namespace'](lifecycle['BACKEND'])
    report['checks'] = {'sameBackendImageAndMounts': all(current[k] == old[k] for k in ('image', 'mounts')),
                        'samePeerServiceSet': set(actual) == set(services),
                        'peerImagesMountsAndNamespacePreserved': all(p['service'] in actual and all(
                            p[k] == actual[p['service']][k] for k in ('image', 'mounts')) and
                            actual[p['service']]['namespace'] == expected_namespace for p in before['peers'])}
    info = json.loads(subprocess.check_output(['docker', 'inspect', lifecycle['BACKEND']], text=True))[0]
    environment = dict(v.split('=', 1) for v in info['Config']['Env'] if '=' in v)
    report['localRuntimeBudget'] = {'javaOpts': environment.get('JAVA_OPTS'),
                                   'memoryLimitBytes': info['HostConfig']['Memory'],
                                   'nanoCpus': info['HostConfig']['NanoCpus']}
    report.update(backendAfter={k: current[k] for k in ('id', 'image', 'mounts', 'startedAt')},
                  peersAfter=peers, finishedAt=dt.datetime.now(dt.timezone.utc).isoformat())
    report['status'] = 'PASS_OWNED_STACK_RESTORED' if all(report['checks'].values()) else 'FAIL_RESTORE_CHECK'
    save()
    print(json.dumps({k: report[k] for k in ('status', 'checks', 'localRuntimeBudget')}))
    if report['status'] != 'PASS_OWNED_STACK_RESTORED':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pause-evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    main(args.pause_evidence, args.output)
