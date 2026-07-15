#!/usr/bin/env python3
"""Keep all running acceptance MCP peers attached to the backend's current network.

Docker peers using network_mode=service:backend retain an obsolete namespace after
a standalone backend restart. Discover peers from Compose labels, suspend before
the change, and restore in finally. Containers/images/volumes and credentials remain.
"""
from contextlib import contextmanager
from pathlib import Path
import argparse
import json
import runpy
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BACKEND = 'orbisops-acceptance-backend-1'
PROJECT = 'orbisops-acceptance'
PROFILES = ('--profile', 'business', '--profile', 'landing', '--profile', 'discovery', '--profile', 'platform-eval', '--profile', 'platform-closure')


def bind_source(source):
    # Docker Desktop can report the same macOS bind as either a host path or its
    # VM /host_mnt alias after recreation. Normalize only existing repo paths;
    # external paths and volume identities still require exact equality.
    alias = '/host_mnt' + str(ROOT) + '/'
    if sys.platform == 'darwin' and source.startswith(alias):
        host_path = source[len('/host_mnt'):]
        if Path(host_path).exists():
            return host_path
    return source


def inspect(name):
    # Capture only operational identity; never copy container environment credentials.
    template = '{{json .Id}}\n{{json .State}}\n{{json .HostConfig.NetworkMode}}\n{{json .Config.Labels}}\n{{json .Mounts}}\n{{json .Image}}'
    values = subprocess.check_output(['docker', 'inspect', name, '--format', template], text=True).splitlines()
    ident, state, network, labels, mounts, image = map(json.loads, values)
    return {'id': ident, 'running': state['Running'], 'startedAt': state['StartedAt'],
            'health': state.get('Health', {}).get('Status'), 'networkMode': network, 'labels': labels,
            'mounts': sorted([{'type': m['Type'], 'name': m.get('Name', ''),
                              'source': bind_source(m['Source']) if m['Type'] == 'bind' else m['Source'],
                              'target': m['Destination'], 'readOnly': not m['RW']} for m in mounts],
                             key=lambda item: (item['target'], item['type'], item['source'], item['name'])), 'image': image}


def namespace(name):
    return subprocess.check_output(['docker', 'exec', name, 'readlink', '/proc/1/ns/net'], text=True).strip()


def running_peers():
    backend = inspect(BACKEND)
    if backend['labels'].get('com.docker.compose.project') != PROJECT or not backend['running']:
        raise RuntimeError('Only the running local acceptance backend is supported')
    names = subprocess.check_output(['docker', 'ps', '--filter', 'label=com.docker.compose.project=' + PROJECT,
                                     '--format', '{{.Names}}'], text=True).splitlines()
    peers = []
    for name in names:
        item = inspect(name)
        if item['networkMode'] not in ('container:' + backend['id'], 'container:' + BACKEND):
            continue
        service = item['labels'].get('com.docker.compose.service')
        if not service:
            raise RuntimeError('A shared-network peer has no Compose service identity')
        item.update(name=name, service=service, namespace=namespace(name))
        item.pop('labels')
        peers.append(item)
    return backend, sorted(peers, key=lambda item: item['service'])


def wait_backend():
    deadline = time.monotonic() + 120
    while True:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18089/api/v1/setup/status', timeout=3) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise TimeoutError('Acceptance backend did not become ready')
        time.sleep(1)


@contextmanager
def suspended_peers():
    backend, peers = running_peers()
    report = {'backendBefore': {'id': backend['id'], 'startedAt': backend['startedAt'], 'namespace': namespace(BACKEND)},
              'peersBefore': peers, 'peersAfter': [], 'previouslyStoppedPeersStarted': False}
    compose = runpy.run_path(str(ROOT / 'scripts/local-acceptance.py'))['compose']
    services = [item['service'] for item in peers]
    try:
        if services:
            compose(*PROFILES, 'stop', *services)
        yield report
    finally:
        # force-recreate joins the live backend namespace even when its container ID is unchanged.
        if services:
            compose(*PROFILES, 'up', '-d', '--no-deps', '--no-build', '--force-recreate', *services)
        expected_namespace = namespace(BACKEND)
        deadline = time.monotonic() + 45
        for old in peers:
            while True:
                current = inspect(old['name'])
                if current['running'] and current['health'] not in ('starting', 'unhealthy'):
                    current_namespace = namespace(old['name'])
                    if current_namespace == expected_namespace:
                        break
                if time.monotonic() >= deadline:
                    raise TimeoutError('Shared-network peer did not rejoin the backend: ' + old['service'])
                time.sleep(1)
            if current['image'] != old['image'] or current['mounts'] != old['mounts']:
                raise RuntimeError('Peer image or persisted mount changed: ' + json.dumps({
                    'service': old['service'], 'before': {'image': old['image'], 'mounts': old['mounts']},
                    'after': {'image': current['image'], 'mounts': current['mounts']}}))
            report['peersAfter'].append({'name': old['name'], 'service': old['service'], 'namespace': current_namespace,
                                         'startedAt': current['startedAt'], 'imageAndMountsPreserved': True})
        report['backendAfter'] = {'id': inspect(BACKEND)['id'], 'startedAt': inspect(BACKEND)['startedAt'], 'namespace': expected_namespace}


def restart_backend():
    admission = runpy.run_path(str(ROOT / 'scripts/deploy-tested-acceptance.py'))['require_idle_runs']()
    with suspended_peers() as report:
        subprocess.run(['docker', 'restart', BACKEND], check=True, stdout=subprocess.DEVNULL)
        wait_backend()
    report['admission'] = admission
    return report


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repair-peers-only', action='store_true')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if args.output.exists():
        raise ValueError('Use a fresh evidence path')
    if args.repair_peers_only:
        admission = runpy.run_path(str(ROOT / 'scripts/deploy-tested-acceptance.py'))['require_idle_runs']()
        with suspended_peers() as result:
            pass
        result['admission'] = admission
    else:
        result = restart_backend()
    result['status'] = 'PASS'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': 'PASS', 'peersRestored': len(result['peersAfter']), 'backendRestarted': not args.repair_peers_only}))
