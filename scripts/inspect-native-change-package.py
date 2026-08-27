#!/usr/bin/env python3
"""Collect existing package, approvals and Landing evidence without replaying an action."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main(package, project, output):
    if output.exists():
        raise ValueError('Prior evidence retained; use a fresh output')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    base = '/api/v1/admin/ops/change-packages/' + package
    detail = h['api'](base, token=h['token'])
    if detail.get('projectId') != project:
        raise ValueError('Native project differs from requested evidence scope')
    records = {'package': detail,
        'events': h['api'](base + '/events?limit=100', token=h['token']),
        'landingOperationRuns': h['api'](base + '/landing-operation-runs?limit=200', token=h['token']),
        'landingEvents': h['api'](base + '/landing-events?limit=100', token=h['token'])}
    jar = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
        'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0]
    result = {'recordedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'collectorSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'deployedJarSha256': jar, 'newExecutions': 0,
        'boundary': 'Retained native records only; no inferred TaskAcceptance or business success.',
        **records}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'packageId': package, 'projectId': project,
        'status': detail.get('status'), 'approvedVersion': detail.get('approvedVersion'),
        'packageHash': detail.get('packageHash'), 'output': str(output)}, ensure_ascii=False))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--package', required=True)
    p.add_argument('--project', required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    main(a.package, a.project, a.output)
