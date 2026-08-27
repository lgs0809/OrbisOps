#!/usr/bin/env python3
"""Connect only the acceptance backend to the existing strict model routes."""
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main():
    query = "SELECT COUNT(*) FROM ai_ops_agent_run WHERE status='RUNNING'"
    result = subprocess.run(['docker', 'exec', '-i', 'orbisops-acceptance-mysql-1', 'sh', '-c',
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B orbisops_acceptance'],
                            input=query, text=True, capture_output=True, check=True)
    if int(result.stdout.strip()) != 0:
        raise SystemExit('Active WorkSessions exist; finish them before restarting the acceptance backend.')
    with urllib.request.urlopen('http://127.0.0.1:8110/ready', timeout=5) as response:
        assert json.load(response)['ready']
    env = ROOT / 'deploy/.env.acceptance'
    backup = ROOT / 'deploy/.acceptance-private/acceptance-before-retrieval.env'
    if not backup.exists():
        shutil.copy2(env, backup)
        backup.chmod(0o600)
    key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
    updates = {'ORBISOPS_SKILL_RETRIEVAL_ENDPOINT': 'http://host.docker.internal:8110/orbisops',
               'ORBISOPS_SKILL_RETRIEVAL_API_KEY': key,
               'ORBISOPS_SKILL_EMBEDDING_REVISION': '9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
               'ORBISOPS_SKILL_RERANKER_REVISION': '4bd860ac4f15ad1897a214615cccc700f8f71818'}
    lines = env.read_text().splitlines()
    seen = set()
    for index, line in enumerate(lines):
        name = line.split('=', 1)[0]
        if name in updates:
            lines[index] = name + '=' + updates[name]
            seen.add(name)
    lines.extend(name + '=' + value for name, value in updates.items() if name not in seen)
    env.write_text('\n'.join(lines) + '\n')
    compose = runpy.run_path(str(ROOT / 'scripts/local-acceptance.py'))['compose']
    peers = ['mcp-acceptance', 'observability-mcp', 'landing-mcp']
    try:
        compose('--profile', 'business', '--profile', 'landing', 'stop', *peers)
        compose('up', '-d', '--no-build', 'backend')
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen('http://127.0.0.1:18089/api/v1/setup/status', timeout=3) as response:
                    assert response.status == 200
                print('Acceptance backend started with existing model routes; inspect actual generation and fallback results.')
                return
            except OSError:
                time.sleep(2)
        raise RuntimeError('Acceptance backend did not become ready')
    finally:
        compose('--profile', 'business', '--profile', 'landing', 'up', '-d', '--no-deps', '--no-build', *peers)


if __name__ == '__main__':
    main()
