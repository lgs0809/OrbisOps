#!/usr/bin/env python3
"""Read-only counters, lease, immutable source and audit for one local background job."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(job, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new evidence path')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    column_query = "SELECT JSON_OBJECT('present',COUNT(*)) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_ops_skill_evolution_job_state' AND column_name='ordinary_failures'"
    migrated = h['rows'](column_query)[0]['present'] == 1
    counter = 's.ordinary_failures' if migrated else 'NULL'
    queries = {
        'migration': "SELECT JSON_OBJECT('version',version,'checksum',checksum,'appliedAt',applied_at) FROM orbisops_schema_history WHERE version='100'",
        'job': """SELECT JSON_OBJECT('jobId',j.job_id,'runId',j.run_id,'projectId',j.project_id,
          'sessionId',j.session_id,'agentId',j.agent_id,'status',j.status,'attempts',j.attempts,
          'ordinaryFailureCount',""" + counter + """,'nextRunAt',j.next_run_at,
          'lastErrorHash',SHA2(COALESCE(j.last_error,''),256),'sourceId',s.source_id,
          'epoch',s.epoch,'leaseUntilMs',s.lease_until_ms)
          FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
          WHERE j.project_id='ops-acceptance-a' AND j.job_id=""" + h['quoted'](job),
        'source': """SELECT JSON_OBJECT('sourceId',f.source_id,'inputHash',f.input_hash,
          'actualInputHash',SHA2(f.input_json,256),'candidateId',f.candidate_id,
          'episodeId',f.episode_id,'episodeRevision',f.episode_revision)
          FROM ai_ops_skill_evolution_source f JOIN ai_ops_skill_evolution_job_state s ON s.source_id=f.source_id
          WHERE f.project_id='ops-acceptance-a' AND s.job_id=""" + h['quoted'](job),
        'audit': "SELECT JSON_OBJECT('id',id,'action',action_name,'afterHash',SHA2(COALESCE(after_json,''),256)) FROM ai_ops_config_audit WHERE project_id='ops-acceptance-a' AND module_name='skill-evolver' AND target_id=" + h['quoted'](job) + ' ORDER BY id',
    }
    facts = {key: h['rows'](query) for key, query in queries.items()}
    if len(facts['job']) != 1 or len(facts['source']) != 1:
        raise ValueError('An exact existing accepted local job is required')
    source = facts['source'][0]
    checks = {'immutable_source_hash_matches': source['inputHash'] == source['actualInputHash'],
              'job_and_source_identity_match': facts['job'][0]['sourceId'] == source['sourceId']}
    result = {'status': 'PASS_READ_INTEGRITY' if all(checks.values()) else 'FAIL_INTEGRITY',
              'checks': checks, 'counterColumnPresent': migrated, 'facts': facts,
              'boundary': 'Read-only observation; queue recovery or model authoring is verified separately.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(column_query + ';\n' + ';\n'.join(queries.values()) + ';\n')
    result['sqlSha256'] = hashlib.sha256(output.with_suffix('.sql').read_bytes()).hexdigest()
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'jobStatus': facts['job'][0]['status'],
                      'attempts': facts['job'][0]['attempts'], 'ordinaryFailures': facts['job'][0]['ordinaryFailureCount']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.job, args.output)
