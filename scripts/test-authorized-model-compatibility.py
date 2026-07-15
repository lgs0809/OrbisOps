#!/usr/bin/env python3
"""Run the opt-in Spring AI smoke test against the existing authorized model gateway.

An ephemeral loopback relay keeps the real credential out of Maven arguments and
test reports. It forwards the request and successful response bytes unchanged;
there are no fixed replies, model substitutions, or transport retries.
"""
import argparse
import json
from pathlib import Path
import runpy
import subprocess
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]


def main(output):
    if output.exists() or output.with_suffix('.log').exists():
        raise ValueError('Choose new output names to retain prior results')
    config = runpy.run_path(str(ROOT / 'scripts/enable-acceptance-models.py'))
    values = config['read_env'](ROOT / 'deploy/.env.acceptance')
    decoded = config['decoded']
    if decoded(values.get('ORBISOPS_COMPOSE_PROJECT_NAME', '')) != 'orbisops-acceptance':
        raise ValueError('Only the isolated acceptance configuration is allowed')
    if decoded(values.get('ORBISOPS_AI_MODEL_CALLS_ENABLED', '')) != 'true':
        raise ValueError('Authorized model calls are not enabled')
    base = decoded(values.get('ORBISOPS_MODEL_BASE_URL', '')).rstrip('/')
    key = decoded(values.get('ORBISOPS_MODEL_API_KEY', ''))
    endpoint = urlparse(base)
    if endpoint.scheme != 'https' or not endpoint.hostname or endpoint.username or endpoint.password or not key:
        raise ValueError('An existing HTTPS provider and credential are required')
    api = runpy.run_path(str(ROOT / 'scripts/seed-local-acceptance.py'))
    token = api['login'](json.loads((ROOT / 'deploy/.acceptance-private/admin.json').read_text()))
    providers = api['request']('/api/v1/admin/ai-client-api/query-all', token=token)
    registered = next(v for v in providers if v['apiId'] == 'ops-acceptance-authorized-models')
    if registered['baseUrl'].rstrip('/') != base or registered['status'] != 1:
        raise ValueError('The registered provider differs from the private acceptance configuration')
    path = registered['completionsPath']
    if path != '/v1/chat/completions':
        raise ValueError('The registered completions path differs; retain it for investigation')
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'status': 'FAIL', 'scope': 'Actual authorized gateway; exact Spring AI URL and provider response identity',
              'attempts': [], 'relayRetries': 0, 'credentialsPrinted': False}
    relay = subprocess.Popen(['node', str(ROOT / 'scripts/authorized-model-smoke-relay.mjs')],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    try:
        # Credentials travel only through a private anonymous pipe, never argv,
        # the environment, a report, or an on-disk relay configuration.
        relay.stdin.write(json.dumps({'url': base + path, 'key': key}) + '\n')
        relay.stdin.close()
        ready = json.loads(relay.stdout.readline())
        url = 'http://127.0.0.1:' + str(ready['port'])
        command = ['mvn', '-B', '-DsocksNonProxyHosts=localhost|127.*|[::1]', '-pl', 'orbisops-app', '-am',
                   '-Dtest=OpsOpenAiCompatibilityRealSmokeTest', '-Dsurefire.failIfNoSpecifiedTests=false',
                   '-Dreal.model.relay.url=' + url, 'test']
        with output.with_suffix('.log').open('xb') as log:
            completed = subprocess.run(command, cwd=ROOT / 'server', stdout=log, stderr=subprocess.STDOUT, timeout=180)
        result['exitCode'] = completed.returncode
    finally:
        relay.terminate()
        try:
            relay.wait(timeout=50)
        except subprocess.TimeoutExpired:
            relay.kill()
            relay.wait(timeout=5)
        for line in relay.stdout:
            record = json.loads(line)
            if 'attempts' in record:
                result['attempts'] = record['attempts']
        result['status'] = 'PASS' if (result.get('exitCode') == 0 and len(result['attempts']) == 1
                                    and result['attempts'][0].get('returnedModel') == 'gpt-5.6-luna') else 'FAIL'
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    raise SystemExit(main(parser.parse_args().output))
