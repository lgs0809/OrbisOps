#!/usr/bin/env python3
"""Advance one isolated pending Skill evidence retry after a fix; normal worker owns all processing.

This is a test scheduling control, not a seed of acceptance, method, publication or completion.
The before record and exact guarded SQL are retained. Never resets attempts or leases.
"""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def main(job_id, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Retain earlier evidence; choose a new output')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    api, token, quote, sql = [support[k] for k in ('api', 'token', 'quoted', 'sql')]
    endpoint = '/api/v1/admin/ops/skill-evolver/jobs/' + job_id
    before = api(endpoint, token=token)
    if before['projectId'] != 'ops-acceptance-a' or before['status'] != 'PENDING' or before['leaseUntilMillis'] != 0:
        raise ValueError('Requires an idle pending job in the isolated acceptance project')
    if before.get('lastFailureCode') not in ('SKILL_AUTHORING_INPUT_TOO_LARGE', 'SKILL_EVIDENCE_INPUT_DEFERRED', 'BACKGROUND_MODEL_TIMEOUT', 'SKILL_AUTHORING_MODEL_INVALID'):
        raise ValueError('Not a previously recorded evidence/model deferral')
    statement = """-- Test scheduling only: bring one previously deferred local acceptance job due.
UPDATE ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
SET j.next_run_at=CURRENT_TIMESTAMP
WHERE j.project_id='ops-acceptance-a' AND j.status='PENDING'
  AND s.lease_token='' AND s.lease_until_ms=0
  AND j.last_error IN ('SKILL_GROUPING_DEFERRED','SKILL_EVIDENCE_INPUT_DEFERRED')
  AND j.job_id=""" + quote(job_id) + ';\nSELECT ROW_COUNT();\n'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(statement)
    changed = sql(statement).strip()
    after = api(endpoint, token=token)
    result = {'status': 'RETRY_BROUGHT_DUE_ONLY', 'updatedRows': changed, 'before': before, 'after': after,
              'boundary': 'Only next_run_at changes. Wait for the normal worker and inspect actual model/read/DB results separately.'}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'jobId': job_id, 'updatedRows': changed, 'status': after['status']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    main(args.job, args.output)
