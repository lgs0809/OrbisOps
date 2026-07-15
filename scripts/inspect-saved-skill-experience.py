#!/usr/bin/env python3
"""Compare a deployed task detail with MySQL, and reject unauthorized readers. Read-only."""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(job_id, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new evidence path')
    h = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    path = '/api/v1/admin/ops/skill-evolver/jobs/' + job_id
    query = """SELECT JSON_OBJECT('sourceId',s.source_id,'methodHash',f.method_hash,
      'actualHash',SHA2(f.method_json,256),'method',CAST(f.method_json AS JSON),'groupId',f.group_id,'status',f.status)
      FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
      JOIN ai_ops_skill_method_experience f ON f.project_id=j.project_id AND f.source_id=s.source_id
      WHERE j.project_id='ops-acceptance-a' AND j.job_id=""" + h['quoted'](job_id)
    result = {'status': 'NOT_COMPLETED', 'jobId': job_id, 'permissions': {}}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query+';\n')
    try:
        rows = h['rows'](query)
        assert len(rows) == 1
        stored = rows[0]
        shown = h['api'](path, token=h['token'])['savedExperience']
        assert stored['methodHash'] == stored['actualHash']
        for key in ('sourceId', 'methodHash', 'method', 'groupId', 'status'):
            assert shown[key] == stored[key], key
        assert not {'extractionAudit', 'inputJson', 'providerPayload'} & set(shown)
        result.update(experience=shown, database=stored)
        users = json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text())
        for name in ('ops_acceptance_viewer', 'ops_acceptance_b_member', 'ops_acceptance_operator'):
            response = h['api'](path, token=h['api_module']['login'](users[name]), denied=True)
            assert response['httpStatus'] == 403
            result['permissions'][name] = 403
        assert h['rows'](query) == rows
        result['status'] = 'PASS_DETAIL_INTEGRITY_AND_ACCESS'
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': result['status'], 'methodHash': shown['methodHash'], 'permissions': result['permissions']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.job, args.output)
