#!/usr/bin/env python3
"""Verify the creator cannot approve an already REVIEWING local acceptance package."""
import argparse
import json
from pathlib import Path
import runpy
import urllib.request
import urllib.error


def run(package_id, output):
    root = Path(__file__).resolve().parents[1]
    h = runpy.run_path(str(root / 'scripts/test-mcp-runtime.py'))
    route = '/api/v1/admin/ops/change-packages/' + package_id
    before = h['api'](route, token=h['token'])
    assert before['projectId'] == 'ops-acceptance-a' and before['status'] == 'REVIEWING'
    user = next(u for u in h['api']('/api/v1/admin/admin-user/query-all', token=h['token'])
                if u['username'] == 'ops_acceptance_admin')
    assert before['createBy'] == user['userId'], 'This check only tests the actual creator'
    body = {'version': before['version'], 'packageHash': before['packageHash']}
    request = urllib.request.Request('http://127.0.0.1:18089' + route + '/approve',
        json.dumps(body).encode(), {'Authorization': 'Bearer ' + h['token'], 'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        result = {'httpStatus': response.status, 'response': json.load(response)}
    after = h['api'](route, token=h['token'])
    result.update(packageId=package_id, before=before['status'], after=after['status'],
                  version=after['version'], packageHash=after['packageHash'])
    result['status'] = 'NOT_PASSED'
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        assert result['response'].get('code') != '0000'
        assert 'SELF_APPROVAL' in json.dumps(result['response']).upper(), 'Must reach the self-approval gate'
        assert after['status'] == 'REVIEWING' and after['approvedVersion'] == 0
        assert after['packageHash'] == before['packageHash'] and after['version'] == before['version']
        result['status'] = 'PASS_SELF_APPROVAL_DENIED'
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    run(args.package_id, args.output)
