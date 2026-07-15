#!/usr/bin/env python3
"""Check viewer and other-project member approval denial on a REVIEWING acceptance package."""
import argparse
import json
from pathlib import Path
import runpy


def run(package_id, output):
    root = Path(__file__).resolve().parents[1]
    h = runpy.run_path(str(root / 'scripts/test-mcp-runtime.py'))
    users = json.loads((root / 'deploy/.acceptance-private/users.json').read_text())
    route = '/api/v1/admin/ops/change-packages/' + package_id
    before = h['api'](route, token=h['token'])
    assert before['status'] == 'REVIEWING' and before['projectId'] == 'ops-acceptance-a'
    results = []
    for username in ('ops_acceptance_viewer', 'ops_acceptance_b_member'):
        token = h['api_module']['login'](users[username])
        response = h['api']('/api/v1/user/ops/change-packages/' + package_id + '/approve', 'POST',
            {'version': before['version'], 'packageHash': before['packageHash']}, token=token, denied=True)
        assert response['httpStatus'] == 403
        results.append({'username': username, **response})
    after = h['api'](route, token=h['token'])
    assert after['status'] == 'REVIEWING' and after['approvedVersion'] == 0
    assert after['version'] == before['version'] and after['packageHash'] == before['packageHash']
    result = {'status': 'PASS_APPROVAL_PERMISSIONS', 'packageId': package_id, 'checks': results,
              'version': after['version'], 'packageHash': after['packageHash'], 'afterStatus': after['status']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    run(args.package_id, args.output)
