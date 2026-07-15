#!/usr/bin/env python3
"""Read-only evidence for private semantic experience grouping, including MySQL/pgvector consistency.

No synthetic acceptance outcomes or group assignments are written. SQL is retained beside the JSON.
"""
import argparse
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def inspect(output, project):
    for path in (output, output.with_suffix('.sql'), output.with_suffix('.pg.sql')):
        if path.exists():
            raise ValueError('Choose a new evidence filename; previous evidence is retained')
    runtime = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    scope = runtime['quoted'](project)
    queries = {
        'facts': """SELECT JSON_OBJECT('sourceId',f.source_id,'episodeId',f.episode_id,
          'revision',f.episode_revision,'runId',f.run_id,'groupId',f.group_id,'status',f.status,
          'sourceHash',f.source_hash,'methodHash',f.method_hash,'actualMethodHash',SHA2(f.method_json,256),
          'method',CAST(f.method_json AS JSON),'extractionAudit',CAST(f.extraction_audit AS JSON),
          'groupingAudit',IF(f.last_decision IS NULL,NULL,CAST(f.last_decision AS JSON)),
          'currentEpisodeRevision',e.revision,'episodeOutcome',e.outcome,'verifiedOutcome',e.verified_outcome_ref)
          FROM ai_ops_skill_method_experience f LEFT JOIN ai_ops_task_episode e
          ON e.project_id=f.project_id AND e.episode_id=f.episode_id WHERE f.project_id=""" + scope + ' ORDER BY f.created_at,f.source_id',
        'groups': """SELECT JSON_OBJECT('groupId',g.group_id,'version',g.version,'hash',g.content_hash,
          'method',CAST(g.method_json AS JSON),'versionSources',CAST(v.sources_json AS JSON))
          FROM ai_ops_skill_method_group g LEFT JOIN ai_ops_skill_method_group_version v
          ON v.group_id=g.group_id AND v.version=g.version WHERE g.project_id=""" + scope + ' ORDER BY g.group_id',
        'jobs': """SELECT JSON_OBJECT('jobId',j.job_id,'runId',j.run_id,'status',j.status,'attempts',j.attempts,
          'error',j.last_error,'nextRunAt',j.next_run_at,'sourceId',s.source_id,'trigger',j.trigger_reason,
          'leaseEpoch',s.epoch,'leaseUntilMs',s.lease_until_ms,'observedAtMs',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,
          'evidenceFailureReason',(SELECT REGEXP_SUBSTR(JSON_UNQUOTE(JSON_EXTRACT(a.after_json,'$.error')),'SKILL_EVIDENCE_[A-Z_]+',1,2)
            FROM ai_ops_config_audit a WHERE a.project_id=j.project_id AND a.target_id=j.job_id
            AND a.module_name='skill-evolver' AND a.action_name='job-fail'
            AND JSON_EXTRACT(a.after_json,'$.attempts')=j.attempts ORDER BY a.id DESC LIMIT 1),
          'decision',(SELECT p.decision FROM ai_ops_skill_evolution_patch p WHERE p.job_id=j.job_id ORDER BY p.id DESC LIMIT 1))
          FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
          WHERE j.project_id=""" + scope + ' ORDER BY j.id',
    }
    result = {name: runtime['rows'](query) for name, query in queries.items()}
    # SQL-standard quoting; no shell interpolation of the project or credentials.
    pg_scope = "'" + project.replace("'", "''") + "'"
    pg = "SELECT json_build_object('groupId',group_id,'version',group_version,'hash',content_hash,'model',model_identity,'projectionKind',projection_kind) FROM ops_skill_experience_group_index WHERE project_id=" + pg_scope + ' ORDER BY group_id,model_identity;'
    raw = subprocess.check_output(['docker','exec','-i','orbisops-acceptance-pgvector-1','sh','-c',
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1'],input=pg,text=True)
    result['index'] = [json.loads(line) for line in raw.splitlines() if line]
    result['mismatches'] = [f['sourceId'] for f in result['facts'] if f['methodHash'] != f['actualMethodHash']]
    result['projectionPending'] = [g['groupId'] for g in result['groups'] if not any(
        i['groupId'] == g['groupId'] and i['version'] == g['version'] and i['hash'] == g['hash']
        and i['projectionKind'] == 'fact-centroid-v1' for i in result['index'])]
    result['status'] = 'FAIL_INTEGRITY' if result['mismatches'] else 'NOT_EXERCISED' if not result['facts'] else 'OBSERVED'
    result['boundary'] = 'OBSERVED verifies persisted evidence only; method compatibility and publication require separate review.'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries.values())+';\n')
    output.with_suffix('.pg.sql').write_text(pg+'\n')
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':result['status'],'facts':len(result['facts']),'groups':len(result['groups']),
                     'projectionPending':result['projectionPending'],'mismatches':result['mismatches']},ensure_ascii=False))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project',default='ops-acceptance-a')
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    inspect(args.output,args.project)
