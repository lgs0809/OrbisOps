#!/usr/bin/env python3
"""Add one isolated acceptance maintainer for the existing high-risk approval quorum."""
import argparse
import json
from pathlib import Path
import runpy
import secrets


def run(output):
    root = Path(__file__).resolve().parents[1]
    h = runpy.run_path(str(root / 'scripts/test-mcp-runtime.py'))
    helper = h['api_module']
    name = 'ops_acceptance_reviewer'
    credentials = helper['private_json']('landing-reviewer.json', lambda: {
        'username': name, 'password': secrets.token_urlsafe(28)})
    users = h['api']('/api/v1/admin/admin-user/query-all', token=h['token'])
    if not any(user['username'] == name for user in users):
        h['api']('/api/v1/admin/admin-user/create', 'POST',
            {**credentials, 'userId': name, 'userRole': 'user', 'status': 1}, token=h['token'])
        users = h['api']('/api/v1/admin/admin-user/query-all', token=h['token'])
    user = next(user for user in users if user['username'] == name)
    helper['login'](credentials)
    route = '/api/v1/admin/ops-projects/projects/ops-acceptance-a/members'
    members = helper['normalized_members'](h['api'](route, token=h['token']))
    existing = next((m for m in members if m['userId'] == user['userId']), None)
    if existing:
        assert existing['memberRole'] == 'MAINTAINER', 'Existing membership preserved; incompatible role'
    else:
        h['api'](route, 'PUT', {'members': members + [
            {'userId': user['userId'], 'username': name, 'memberRole': 'MAINTAINER'}]}, token=h['token'])
    after = helper['normalized_members'](h['api'](route, token=h['token']))
    assert all(member in after for member in members)
    result = {'status': 'READY', 'projectId': 'ops-acceptance-a', 'username': name,
              'userId': user['userId'], 'role': 'MAINTAINER', 'existingMembersPreserved': True}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    run(parser.parse_args().output)
