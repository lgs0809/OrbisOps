#!/usr/bin/env python3
"""Explicitly retry one failed accepted Skill task through the authorized application API.

Preserves source identity and failure audits. Never writes queue, acceptance or publication SQL.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def safe(job):
    fields = ('jobId', 'runId', 'sessionId', 'projectId', 'agentId', 'sourceId',
              'status', 'attempts', 'nextRunAt', 'epoch', 'lastFailureCode')
    return {**{key: job.get(key) for key in fields},
            'lastErrorSha256': hashlib.sha256(str(job.get('lastError', '')).encode()).hexdigest()}


def main(job_id, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a fresh evidence path')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    endpoint = '/api/v1/admin/ops/skill-evolver/jobs'
    before = h['api'](endpoint + '/' + job_id, token=h['token'])
    if before['projectId'] != 'ops-acceptance-a' or before['status'] != 'FAILED':
        raise ValueError('Requires a failed task in the isolated acceptance project')
    result = {'status': 'REQUEST_NOT_SUBMITTED', 'before': safe(before),
              'boundary': 'Queue recovery only; publication and model reasoning require separate evidence.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    query = "SELECT JSON_OBJECT('jobId',j.job_id,'status',j.status,'attempts',j.attempts,'sourceId',s.source_id,'epoch',s.epoch) FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id WHERE j.job_id=" + h['quoted'](job_id)
    result['databaseBefore'] = h['rows'](query)
    if len(result['databaseBefore']) != 1 or not result['databaseBefore'][0]['sourceId']:
        raise ValueError('A frozen accepted source is required')
    output.with_suffix('.sql').write_text(query + ';\n')
    try:
        command = {key: before[key] for key in ('runId', 'sessionId', 'projectId', 'agentId')}
        command['triggerReason'] = 'MANUAL_RETRY'
        h['api'](endpoint, 'POST', command, h['token'])
        result['status'] = 'REQUEST_SUBMITTED_VERIFICATION_PENDING'
        after = h['api'](endpoint + '/' + job_id, token=h['token'])
        result['after'] = safe(after)
        result['database'] = h['rows'](query)
        assert after['jobId'] == before['jobId']
        assert len(result['database']) == 1
        assert result['database'][0]['sourceId'] == result['databaseBefore'][0]['sourceId']
        assert after['status'] in ('PENDING', 'RUNNING', 'SKIPPED', 'NO_CHANGE', 'COMPLETED')
        result['status'] = 'PASS_QUEUE_RECOVERY_ONLY'
    except Exception as error:
        result['errorType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'jobId': job_id, 'status': result['status']}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    main(args.job, args.output)
