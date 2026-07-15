#!/usr/bin/env python3
"""Start an isolated, bounded 503 fault injector and seed its explicitly selected Luna binding.

The ordinary project default stays unchanged. Stop the named container after the browser test.
Its transport ledger is retained in --state-dir. This calls no model by itself.
"""
from pathlib import Path
import argparse
import json
import os
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = 'orbisops-acceptance-model-fault'


def main(state_dir, reenable_test_model=False):
    config = runpy.run_path(str(ROOT / 'scripts/enable-acceptance-models.py'))
    env = config['read_env'](ROOT / 'deploy/.env.acceptance')
    state_dir = state_dir.resolve()
    state_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    if subprocess.run(['docker', 'inspect', CONTAINER], capture_output=True).returncode == 0:
        raise ValueError('An existing recovery-test container was retained; stop/reuse it explicitly')
    task_env = dict(os.environ, MODEL_FAULT_UPSTREAM=config['decoded'](env['ORBISOPS_MODEL_BASE_URL']),
                    MODEL_FAULT_API_KEY=config['decoded'](env['ORBISOPS_MODEL_API_KEY']))
    subprocess.run(['docker', 'run', '-d', '--pull=never', '--name', CONTAINER,
        '--label', 'orbisops.acceptance.model-fault=true',
        '--network', 'container:orbisops-acceptance-backend-1', '--read-only',
        '--cap-drop=ALL', '--security-opt=no-new-privileges:true', '--memory=128m', '--cpus=0.5',
        '--user', str(os.getuid()) + ':' + str(os.getgid()), '--entrypoint', 'python3',
        '--mount', 'type=bind,source=' + str(ROOT/'deploy/acceptance/model-fault-proxy.py') + ',target=/fault-proxy.py,readonly',
        '--mount', 'type=bind,source=' + str(state_dir) + ',target=/state',
        '-e', 'MODEL_FAULT_UPSTREAM', '-e', 'MODEL_FAULT_API_KEY',
        'orbisops/server:2.0.0-acceptance', '/fault-proxy.py'], env=task_env, check=True, stdout=subprocess.DEVNULL)
    ready = "import urllib.request; assert urllib.request.urlopen('http://127.0.0.1:8382/ready', timeout=2).status==200"
    for _ in range(20):
        probe = subprocess.run(['docker', 'exec', CONTAINER, 'python3', '-c', ready], capture_output=True)
        if probe.returncode == 0: break
        time.sleep(0.5)
    else: raise RuntimeError('The isolated proxy did not become ready')
    helper = runpy.run_path(str(ROOT / 'scripts/seed-local-acceptance.py'))
    token = helper['login'](json.loads((ROOT / 'deploy/.acceptance-private/admin.json').read_text()))
    api = helper['request']
    provider = {'apiId': 'ops-acceptance-model-recovery', 'providerName': '本地网络恢复验收（转发已授权网关）',
                'providerType': 'openai', 'baseUrl': 'http://127.0.0.1:8382',
                'apiKey': '${env:ORBISOPS_MODEL_API_KEY:}', 'completionsPath': '/v1/chat/completions',
                'embeddingsPath': '/v1/embeddings', 'status': 1}
    model = {'modelId': 'ops-acceptance-luna-recovery', 'apiId': provider['apiId'], 'modelName': 'gpt-5.6-luna',
             'modelType': 'openai', 'modelUsage': 'CHAT', 'description': '仅用于本地网络故障恢复验收；仍由真实 Luna 推理', 'status': 1}
    for kind, desired, identity, fields in [('api', provider, 'apiId', ['baseUrl', 'status']),
                                           ('model', model, 'modelId', ['apiId', 'modelName', 'status'])]:
        path = '/api/v1/admin/ai-client-' + kind
        current = next((v for v in api(path + '/query-all', token=token) if v[identity] == desired[identity]), None)
        if current is None:
            assert api(path + '/create', 'POST', desired, token) is True
        elif (kind == 'model' and reenable_test_model and current.get('status') == 0
              and all(current.get(k) == desired[k] for k in ('apiId', 'modelName', 'description'))):
            assert api(path + '/update-by-model-id', 'PUT', {**current, 'status': 1}, token) is True
        elif any(current.get(k) != desired[k] for k in fields):
            raise ValueError('Conflicting or disabled existing binding retained; use --reenable-test-model only for a new fault test')
    result = {'status': 'READY', 'container': CONTAINER, 'modelId': model['modelId'],
              'requestedModel': model['modelName'], 'injectedFirstRequests': 2, 'maxPhysicalRequests': 24,
              'projectDefaultChanged': False, 'stateDir': str(state_dir)}
    (state_dir/'setup.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-dir', required=True, type=Path)
    parser.add_argument('--reenable-test-model', action='store_true', help='Explicitly re-enable this dedicated test model after prior test cleanup')
    args = parser.parse_args()
    main(args.state_dir, args.reenable_test_model)
