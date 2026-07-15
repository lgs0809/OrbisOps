#!/usr/bin/env python3
"""Read-only deployed API/DB check of a retained author conclusion, including denied access."""
import argparse
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(job, output):
    if not re.fullmatch(r'skill-evo-[a-z0-9-]+', job) or output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Exact actual job identity and fresh evidence path required')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    query = """SELECT JSON_OBJECT('jobStatus',j.status,'jobAttempts',j.attempts,'planId',p.plan_id,
      'planHash',p.plan_hash,'actualPlanHash',SHA2(p.input_json,256),
      'authoredHash',p.authored_hash,'actualAuthoredHash',SHA2(p.authored_json,256),
      'sourceHash',f.input_hash,'actualSourceHash',SHA2(f.input_json,256),
      'planSourceHash',JSON_UNQUOTE(JSON_EXTRACT(p.input_json,'$.evolutionSourceHash')),
      'operation',JSON_UNQUOTE(JSON_EXTRACT(p.authored_json,'$.patchType')),
      'reason',JSON_UNQUOTE(JSON_EXTRACT(p.authored_json,'$.reason')),
      'source',JSON_UNQUOTE(JSON_EXTRACT(p.authored_json,'$.authoringSource')),
      'model',JSON_UNQUOTE(JSON_EXTRACT(p.authored_json,'$.authoringModel')),
      'currentSource',(e.revision=f.episode_revision AND e.verified_outcome_ref=f.source_id AND e.outcome='SUCCEEDED'),
      'patchHash',(SELECT SHA2(patch_json,256) FROM ai_ops_skill_evolution_patch WHERE job_id=j.job_id ORDER BY id DESC LIMIT 1))
      FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
      JOIN ai_ops_skill_evolution_proposal p ON p.project_id=j.project_id AND p.job_id=j.job_id AND p.source_id=s.source_id
      JOIN ai_ops_skill_evolution_source f ON f.project_id=j.project_id AND f.source_id=s.source_id
      JOIN ai_ops_task_episode e ON e.project_id=f.project_id AND e.episode_id=f.episode_id
      WHERE j.project_id='ops-acceptance-a' AND j.job_id=""" + h['quoted'](job)
    before = h['rows'](query)
    if len(before) != 1:
        raise ValueError('One retained actual author result is required')
    stored = before[0]
    path = '/api/v1/admin/ops/skill-evolver/jobs/' + job
    detail = h['api'](path, token=h['token'])
    shown = detail.get('authoredDecision') or {}
    checks = {
        'author_input_and_output_hashes_match': stored['planHash'] == stored['actualPlanHash'] and stored['authoredHash'] == stored['actualAuthoredHash'],
        'author_result_belongs_to_exact_immutable_source': stored['sourceHash'] == stored['actualSourceHash'] == stored['planSourceHash'],
        'api_preserves_job_state_and_attempts': detail['status'] == stored['jobStatus'] and detail['attempts'] == stored['jobAttempts'],
        'api_exposes_exact_verified_product_fields': set(shown) == {'planId', 'operation', 'reason', 'source', 'model', 'currentSource'} and all(shown.get(k) == stored[k] for k in ('planId', 'operation', 'reason', 'source', 'model')) and shown.get('currentSource') == bool(stored['currentSource']),
        'valid_no_change_is_not_a_publication': stored['operation'] == 'NO_CHANGE' and stored['jobStatus'] == 'SKIPPED' and bool(stored['reason']) and 'authoredPublication' not in detail,
    }
    users = json.loads((ROOT / 'deploy/.acceptance-private/users.json').read_text())
    denied = {}
    for account in ('ops_acceptance_viewer', 'ops_acceptance_b_member', 'ops_acceptance_operator'):
        response = h['api'](path, token=h['api_module']['login'](users[account]), denied=True)
        denied[account] = response['httpStatus']
        checks['denied_' + account] = response['httpStatus'] == 403
    checks['source_author_and_historical_patch_stay_unchanged'] = h['rows'](query) == before
    report = {'status': 'PASS_AUTHORED_DECISION_AND_ACCESS' if all(checks.values()) else 'FAIL',
              'jobId': job, 'checks': checks, 'shown': shown, 'database': stored, 'denied': denied,
              'boundary': 'API adds a verified read projection. Original source, authored bytes and historical patch are unchanged; no model, publication or acceptance is replayed.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + query + ';\nROLLBACK;\n')
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': report['status'], 'checks': len(checks), 'failed': [k for k, v in checks.items() if not v]}))
    return all(checks.values())


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--job', required=True)
    p.add_argument('--output', required=True, type=Path)
    a = p.parse_args()
    raise SystemExit(0 if inspect(a.job, a.output) else 1)
