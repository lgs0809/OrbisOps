#!/usr/bin/env python3
"""Rebuild only local acceptance sidecars, proving retained SQLite/MySQL state.

No seeds, approvals, business state changes, volume removal or cache cleanup.
Run against an idle acceptance stack; a concurrent business request deliberately
makes the exact before/after assertion fail rather than hiding changed evidence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ('workflow-target', 'prepare-target', 'observability-mcp', 'landing-mcp')
DATABASES = ('ops_acceptance_business_a', 'ops_acceptance_business_prepare')
TABLES = ('acceptance_service', 'ops08_deployment_receipt', 'ops04_request')
SQL = '\n'.join(f'SELECT * FROM {db}.{table};' for db in DATABASES for table in TABLES)
SQLITE_INSPECT = r'''
import glob, hashlib, json, sqlite3
result = {}
for path in sorted(glob.glob('/state/*.sqlite')):
    db = sqlite3.connect('file:' + path + '?mode=ro', uri=True)
    tables = {}
    with db:
        for (table,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
            values = sorted(json.dumps(row, ensure_ascii=False, default=str, separators=(',', ':'))
                            for row in db.execute('SELECT * FROM "' + table.replace('"', '""') + '"'))
            tables[table] = {'rows': len(values), 'sha256': hashlib.sha256('\n'.join(values).encode()).hexdigest()}
    db.close()
    result[path] = tables
print(json.dumps(result))
'''


def command(args):
    return subprocess.check_output(args, text=True)


def snapshot():
    result = {}
    for service in SERVICES:
        container = 'orbisops-acceptance-' + service + '-1'
        details = json.loads(command(['docker', 'inspect', container]))[0]
        image = json.loads(command(['docker', 'image', 'inspect', details['Image']]))[0]
        result[service] = {
            'image': details['Image'], 'imageLayers': len(image['RootFS']['Layers']), 'imageSizeBytes': image['Size'],
            'volumes': sorted(m['Name'] for m in details['Mounts'] if m['Type'] == 'volume'),
            'state': json.loads(command(['docker', 'exec', container, 'python3', '-c', SQLITE_INSPECT]))}
    return result


def main(output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new evidence path')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(SQL + '\n')
    idle = runpy.run_path(str(ROOT / 'scripts/deploy-tested-acceptance.py'))['require_idle_runs']
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    compose = runpy.run_path(str(ROOT / 'scripts/local-acceptance.py'))['compose']
    def mysql_state():
        result = {}
        for db in DATABASES:
            for table in TABLES:
                raw = support['sql'](f'SELECT * FROM {db}.{table};')
                rows = sorted(raw.splitlines())
                result[db + '.' + table] = {'rows': len(rows), 'sha256': hashlib.sha256('\n'.join(rows).encode()).hexdigest()}
        return result
    idle()
    result = {'status': 'NOT_COMPLETED', 'before': snapshot(), 'mysqlBefore': mysql_state()}
    output.write_text(json.dumps(result, indent=2) + '\n')
    try:
        compose('--profile', 'business', '--profile', 'landing', 'build',
                'workflow-target', 'observability-mcp', 'landing-mcp')
        idle()
        compose('--profile', 'business', '--profile', 'landing', 'up', '-d', '--no-deps', '--no-build', *SERVICES)
        deadline = time.monotonic() + 90
        while True:
            health = {name: command(['docker', 'inspect', 'orbisops-acceptance-' + name + '-1',
                                    '--format', '{{.State.Health.Status}}']).strip() for name in SERVICES}
            if all(v == 'healthy' for v in health.values()):
                break
            if time.monotonic() > deadline:
                raise TimeoutError('Acceptance sidecar health check timed out')
            time.sleep(2)
        result.update(after=snapshot(), mysqlAfter=mysql_state(), health=health)
        for name in SERVICES:
            assert result['before'][name]['volumes'] == result['after'][name]['volumes'], name
            assert result['before'][name]['state'] == result['after'][name]['state'], name
        assert result['mysqlBefore'] == result['mysqlAfter'], 'MySQL rows changed during deployment'
        result['status'] = 'PASS_RESTART_STATE_PRESERVED'
    finally:
        output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'services': list(SERVICES)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args().output)
