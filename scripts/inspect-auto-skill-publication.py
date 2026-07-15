#!/usr/bin/env python3
"""Read-only automatic Skill publication evidence. Does not create candidates or acceptance outcomes."""
import argparse
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def inspect(output, project):
    if any(path.exists() for path in (output, output.with_suffix('.sql'), output.with_suffix('.pg.sql'))):
        raise ValueError('Choose a new evidence filename; existing evidence is retained')
    runtime = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    scope = runtime['quoted'](project)
    queries = {
        'releases': """SELECT JSON_OBJECT('releaseId',r.release_id,'candidateId',r.candidate_id,
          'status',r.status,'reason',r.reason_code,'canaryPercent',r.canary_percent,'skillId',r.target_skill_id,
          'version',r.released_version,'skillHash',r.released_skill_hash,'metadata',r.metadata_json,
          'packageHash',v.package_hash,'bodyHash',SHA2(v.content,256),
          'runtimeVersion',p.skill_version,'runtimeHash',p.skill_hash,'runtimePackage',p.package_hash)
          FROM ai_ops_skill_release r
          LEFT JOIN ai_ops_skill_version v ON v.scope='PROJECT' AND v.project_id=r.project_id
            AND v.skill_id=r.target_skill_id AND v.version=r.released_version
          LEFT JOIN ai_ops_skill_runtime_publication p ON p.scope='PROJECT' AND p.project_id=r.project_id
            AND p.skill_id=r.target_skill_id
          WHERE r.project_id=""" + scope + " ORDER BY r.id",
        'atomicGroups': "SELECT JSON_OBJECT('candidateId',candidate_id,'operation',operation,'status',status,'plan',CAST(plan_json AS JSON),'hash',plan_hash,'actualHash',SHA2(plan_json,256),'rollbackActor',rollback_actor,'rollbackReason',rollback_reason) FROM ai_ops_skill_atomic_publication WHERE project_id="+scope+" ORDER BY candidate_id",
        'atomicMembers': """SELECT JSON_OBJECT('candidateId',m.candidate_id,'role',m.role,'skillId',m.skill_id,
          'version',m.skill_version,'skillHash',m.skill_hash,'packageHash',m.package_hash,
          'bodyHash',SHA2(v.content,256),'storedBodyHash',JSON_UNQUOTE(JSON_EXTRACT(v.artifact_hashes_json,'$."SKILL.md"')),
          'versionSkillHash',v.skill_hash,'versionPackageHash',v.package_hash,
          'runtimeVersion',p.skill_version,'runtimeHash',p.skill_hash,'runtimePackage',p.package_hash,'generationId',p.generation_id,
          'replacedBy',r.candidate_id)
          FROM ai_ops_skill_atomic_member m
          LEFT JOIN ai_ops_skill_version v ON v.scope='PROJECT' AND v.project_id=m.project_id AND v.skill_id=m.skill_id AND v.version=m.skill_version
          LEFT JOIN ai_ops_skill_runtime_publication p ON p.scope='PROJECT' AND p.project_id=m.project_id AND p.skill_id=m.skill_id
          LEFT JOIN ai_ops_skill_atomic_replacement r ON r.project_id=m.project_id AND r.source_skill_id=m.skill_id
          WHERE m.project_id="""+scope+" ORDER BY m.candidate_id,m.role,m.skill_id",
        'acceptedTasks': "SELECT JSON_OBJECT('episodeId',episode_id,'revision',episode_revision,'outcome',outcome,'sourceRunId',source_run_id,'recordHash',record_hash) FROM ai_ops_task_acceptance WHERE project_id=" + scope + " ORDER BY created_at",
        'supersededProposals': """SELECT JSON_OBJECT('planId',plan_id,'jobId',job_id,'sourceId',source_id,
          'inputHash',plan_hash,'authoredHash',authored_hash,'inputBytesHash',SHA2(input_json,256),
          'authoredBytesHash',SHA2(authored_json,256),'supersededAt',superseded_at,
          'reason',superseded_reason,'epoch',superseded_epoch)
          FROM ai_ops_skill_evolution_proposal_history WHERE project_id=""" + scope + ' ORDER BY superseded_at,plan_id',
        'proposals': "SELECT JSON_OBJECT('planId',plan_id,'candidateId',candidate_id,'inputHash',plan_hash,'authoredHash',authored_hash) FROM ai_ops_skill_evolution_proposal WHERE project_id=" + scope + " ORDER BY plan_id",
    }
    result = {name: runtime['rows'](sql) for name, sql in queries.items()}
    automatic = [r for r in result['releases'] if json.loads(r['metadata'] or '{}').get('publicationPolicy') == 'source-qualified-auto-v1']
    pg_scope="'"+project.replace("'","''")+"'"
    pg="SELECT json_build_object('generationId',g.generation_id,'skillId',g.skill_id,'version',g.skill_version,'hash',g.skill_hash,'packageHash',g.package_hash,'status',g.status,'dimension',vector_dims(d.embedding)) FROM ops_skill_route_generation g LEFT JOIN ops_skill_route_document d ON d.generation_id=g.generation_id WHERE g.project_id="+pg_scope+" ORDER BY g.generation_id"
    raw=subprocess.check_output(['docker','exec','-i','orbisops-acceptance-pgvector-1','sh','-c',
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1'],input=pg+';',text=True)
    result['indexGenerations']=[json.loads(line) for line in raw.splitlines() if line]
    mismatches=[]
    for release in automatic:
        candidate=release['candidateId']; metadata=json.loads(release['metadata'] or '{}')
        valid=release['canaryPercent']==0
        if metadata.get('atomicPlan'):
            groups=[g for g in result['atomicGroups'] if g['candidateId']==candidate]
            members=[m for m in result['atomicMembers'] if m['candidateId']==candidate]
            valid=valid and len(groups)==1
            if groups:
                group=groups[0];plan=group['plan']
                valid=valid and group['hash']==group['actualHash'] and plan==metadata['atomicPlan']
                expectedStatus={'ACTIVE':'ACTIVE','ROLLED_BACK':'ROLLED_BACK'}.get(release['status'])
                if expectedStatus: valid=valid and group['status']==expectedStatus
                for role,key in [('SOURCE','sources'),('TARGET','targets')]:
                    actual=[m for m in members if m['role']==role]
                    valid=valid and {m['skillId'] for m in actual}=={m['skillId'] for m in plan[key]}
                    for member in actual:
                        valid=valid and member['bodyHash'] and member['bodyHash']==member['storedBodyHash']
                        valid=valid and member['skillHash']==member['versionSkillHash'] and member['packageHash']==member['versionPackageHash']
                        if role=='SOURCE':
                            valid=valid and (member['replacedBy']==candidate if group['status']=='ACTIVE' else member['replacedBy']!=candidate)
                        if role=='TARGET' and group['status']=='ACTIVE':
                            valid=valid and member['runtimeVersion']==member['version'] and member['runtimeHash']==member['skillHash'] and member['runtimePackage']==member['packageHash']
                            valid=valid and any(i['generationId']==member['generationId'] and i['status']=='READY' and i['dimension']==1024
                                and i['hash']==member['skillHash'] and i['packageHash']==member['packageHash'] for i in result['indexGenerations'])
        elif release['status']=='ACTIVE':
            valid=valid and release['version']==release['runtimeVersion'] and release['skillHash']==release['runtimeHash'] and release['packageHash']==release['runtimePackage']
        if not valid:mismatches.append(release['releaseId'])
    result.update(status='NOT_EXERCISED' if not automatic else 'FAIL' if mismatches else 'OBSERVED',
                  automaticReleaseCount=len(automatic), mismatches=mismatches,
                  boundary='Read-only persisted facts; OBSERVED is not a claim of model quality or complete acceptance.')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries.values()) + ';\n')
    output.with_suffix('.pg.sql').write_text(pg+';\n')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: result[k] for k in ('status', 'automaticReleaseCount', 'mismatches')}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--project', default='ops-acceptance-a')
    args = parser.parse_args()
    inspect(args.output, args.project)
