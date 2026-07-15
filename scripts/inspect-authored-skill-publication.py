#!/usr/bin/env python3
"""Cross-check a job's retained proposal through normal authenticated APIs and MySQL; read-only.

The job may be FAILED while its separate release is closed. No raw author/provider payload is exposed.
"""
import argparse
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(job_id, output):
    if not re.fullmatch(r'skill-evo-[a-z0-9-]+', job_id):
        raise ValueError('Use an actual Skill evolution job ID')
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Retain prior evidence; choose a new output')
    h = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    query = """SELECT JSON_OBJECT('candidateId',c.candidate_id,'status',COALESCE(a.status,r.status,c.status),
      'operation',COALESCE(a.operation,c.patch_type),'releasedVersion',COALESCE(r.released_version,0),
      'releaseReason',r.reason_code,'jobStatus',j.status,'jobAttempts',j.attempts,
      'planHash',p.plan_hash,'actualPlanHash',SHA2(p.input_json,256),
      'authoredHash',p.authored_hash,'actualAuthoredHash',SHA2(p.authored_json,256))
      FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
      JOIN ai_ops_skill_evolution_proposal p ON p.project_id=j.project_id AND p.job_id=j.job_id AND p.source_id=s.source_id
      JOIN ai_ops_skill_patch_candidate c ON c.project_id=p.project_id AND c.candidate_id=p.candidate_id
      LEFT JOIN ai_ops_skill_release r ON r.project_id=c.project_id AND r.candidate_id=c.candidate_id
      LEFT JOIN ai_ops_skill_atomic_publication a ON a.project_id=c.project_id AND a.candidate_id=c.candidate_id
      WHERE j.project_id='ops-acceptance-a' AND j.job_id=""" + h['quoted'](job_id) + ' ORDER BY p.create_time DESC,p.plan_id DESC LIMIT 1'
    path = '/api/v1/admin/ops/skill-evolver/jobs/' + job_id
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n'+query+';\nROLLBACK;\n')
    result = {'status': 'FAIL', 'jobId': job_id, 'permissions': {}}
    try:
        before = h['rows'](query)
        assert len(before) == 1, 'No retained candidate belongs to this job/source'
        stored = before[0]
        detail = h['api'](path, token=h['token'])
        shown = detail['authoredPublication']
        for key in ('candidateId', 'status', 'operation', 'releasedVersion'):
            assert shown[key] == stored[key], key
        assert detail['status'] == stored['jobStatus'] and detail['attempts'] == stored['jobAttempts']
        assert stored['planHash'] == stored['actualPlanHash'] and stored['authoredHash'] == stored['actualAuthoredHash']
        assert set(shown) == {'candidateId', 'status', 'operation', 'releasedVersion', 'reason'}
        if (stored.get('releaseReason') or '').startswith('POLICY_PUBLICATION_BASELINE_STALE:'):
            assert shown['reason'] == 'BASELINE_STALE'
        users = json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text())
        for account in ('ops_acceptance_viewer', 'ops_acceptance_b_member', 'ops_acceptance_operator'):
            response = h['api'](path, token=h['api_module']['login'](users[account]), denied=True)
            assert response['httpStatus'] == 403
            result['permissions'][account] = 403
        assert h['rows'](query) == before, 'Read-only verification changed facts'
        result.update(status='PASS_JOB_RELEASE_PROJECTION_AND_ACCESS', publication=shown, database=stored)
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': result['status'], 'publication': shown, 'permissions': result['permissions']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.job, args.output)
