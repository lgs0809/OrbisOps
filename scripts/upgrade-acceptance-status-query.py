#!/usr/bin/env python3
"""Publish a narrowly scoped read-only query capability on the isolated acceptance assistant."""
import argparse
import copy
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def upgrade(output):
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    agent = 'ops-acceptance-a-ops-agent'
    query = "SELECT JSON_OBJECT('definition',definition_json) FROM ai_ops_agent_definition WHERE agent_id=" + h['quoted'](agent)
    record = h['rows'](query)
    assert len(record) == 1
    before = record[0]['definition']
    if isinstance(before, str):
        before = json.loads(before)
    assert before['projectId'] == 'ops-acceptance-a' and before['lifecycle'] == 'PUBLISHED'
    after = copy.deepcopy(before)
    mains = [a for a in after.get('agentscopeAgents', []) if a.get('role') == 'MAIN_ASSISTANT']
    assert len(mains) == 1
    allowed = mains[0]['allowedToolNames']
    assert 'PrepareChangePackage' in allowed
    if 'QueryChangePackageStatus' in allowed:
        print('Already available; nothing changed')
        return
    allowed.insert(allowed.index('PrepareChangePackage'), 'QueryChangePackageStatus')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.before.json').write_text(json.dumps(before, ensure_ascii=False, indent=2))
    output.with_suffix('.sql').write_text(query + ';\n')
    path = '/api/v1/admin/ops-agents/' + agent
    latest = h['api'](path, token=h['token'])
    assert latest['definitionHash'] == before['definitionHash'] and latest['version'] == before['version']
    saved = h['api']('/api/v1/admin/ops-agents/drafts', 'POST', after, h['token'])
    result = {'agentId': agent, 'beforeVersion': before['version'], 'beforeHash': before['definitionHash'],
              'draftVersion': saved['version'], 'status': 'DRAFT_SAVED'}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2))
    for action in ('validate', 'publish'):
        saved = h['api'](path + '/versions/' + str(saved['version']) + '/' + action, 'POST', {}, h['token'])
        result.update(status=saved['lifecycle'], version=saved['version'], definitionHash=saved['definitionHash'])
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    upgrade(parser.parse_args().output)
