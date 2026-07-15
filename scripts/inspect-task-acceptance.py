#!/usr/bin/env python3
"""Read-only acceptance/source audit. This never creates approval, acceptance, or successful tasks."""
import argparse,json,re,subprocess
from pathlib import Path
def assess(rows):
    """Check integrity separately from whether the task's business goal succeeded."""
    episodes = [row for row in rows if row.get('kind') == 'episode']
    acceptances = [row for row in rows if row.get('kind') == 'acceptance']
    if any(row.get('hashMatches', True) != True for row in rows):
        return 'FAIL_INTEGRITY', ['PERSISTED_HASH_MISMATCH']
    if not episodes or not acceptances:
        return 'NOT_EXERCISED', ['EPISODE_OR_ACCEPTANCE_MISSING']
    if len(episodes) != 1:
        return 'FAIL_INTEGRITY', ['EPISODE_ID_NOT_UNIQUE']
    episode = episodes[0]
    current = [row for row in acceptances if row.get('id') == episode.get('verifiedRef')]
    if len(current) != 1:
        return 'NOT_CURRENT', ['NO_CURRENT_VERIFIED_ACCEPTANCE']
    acceptance = current[0]
    if acceptance.get('revision') != episode.get('revision'):
        return 'NOT_CURRENT', ['ACCEPTANCE_REVISION_STALE']
    if acceptance.get('outcome') != episode.get('outcome') or acceptance.get('checks', 0) < 1:
        return 'FAIL_INTEGRITY', ['ACCEPTANCE_OUTCOME_OR_CHECKS_MISMATCH']
    return 'PASS_INTEGRITY', []


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--episode-id',required=True)
    p.add_argument('--output',required=True,type=Path)
    a=p.parse_args()
    if not re.fullmatch(r'task-episode-[a-z0-9-]+',a.episode_id): p.error('Supply an actual task episode ID')
    if a.output.exists() or a.output.with_suffix('.sql').exists(): p.error('Preserve earlier evidence; choose a new output')
    sql=f"""START TRANSACTION READ ONLY;
    SELECT JSON_OBJECT('kind','episode','episodeId',episode_id,'revision',revision,'outcome',outcome,'verifiedRef',verified_outcome_ref)
    FROM ai_ops_task_episode WHERE episode_id='{a.episode_id}';
    SELECT JSON_OBJECT('kind','acceptance','id',acceptance_id,'revision',episode_revision,'outcome',outcome,
     'sourceRun',source_run_id,'hashMatches',SHA2(record_json,256)=record_hash,'created',created_at,
     'checks',JSON_LENGTH(JSON_EXTRACT(record_json,'$.checks')))
    FROM ai_ops_task_acceptance WHERE episode_id='{a.episode_id}' ORDER BY created_at;
    SELECT JSON_OBJECT('kind','receipt','resultId',r.result_id,'status',r.status,'hashMatches',SHA2(r.full_output,256)=r.output_hash)
    FROM ai_ops_tool_result r JOIN ai_ops_task_episode_turn t ON t.source_run_ref=r.run_id AND t.project_id=r.project_id
    WHERE t.episode_id='{a.episode_id}' AND r.source='MCP_REMOTE_TOOL' ORDER BY r.id;
    SELECT JSON_OBJECT('kind','source','sourceId',source_id,'episodeId',episode_id,'revision',episode_revision,
     'candidateId',candidate_id,'hashMatches',SHA2(input_json,256)=input_hash)
    FROM ai_ops_skill_evolution_source WHERE episode_id='{a.episode_id}';
    COMMIT;
    """
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.with_suffix('.sql').write_text(sql)
    r=subprocess.run(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c',
     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw orbisops_acceptance'],
     input=sql,text=True,capture_output=True,check=True,timeout=60)
    rows=[json.loads(line) for line in r.stdout.splitlines()]
    status,reasons=assess(rows)
    report={'status':status,'reasons':reasons,'scope':'READ_ONLY_AUDIT_NOT_NEW_ACCEPTANCE',
            'episodeId':a.episode_id,'rows':rows,'acceptanceCount':sum(row['kind']=='acceptance' for row in rows),
            'independentEpisodes':len({row['episodeId'] for row in rows if row['kind']=='episode'})}
    a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='rows'}))
    raise SystemExit(0 if status == 'PASS_INTEGRITY' else 1)

if __name__ == '__main__':
    main()
