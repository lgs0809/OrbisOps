#!/usr/bin/env python3
"""Read-only automatic Skill publication evidence. Does not create candidates or acceptance outcomes."""
import argparse
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def closed_before_atomic_publication(release, candidate, groups, members):
    reason = release.get('reason') or ''
    return (release.get('status') == 'ROLLED_BACK' and release.get('version') == 0
            and reason.startswith('POLICY_PUBLICATION_BASELINE_STALE:')
            and candidate.get('status') == 'ROLLED_BACK' and candidate.get('reason') == reason
            and candidate.get('versionCount') == 0 and not groups and not members)


def superseding_publication_chain(release, releases, candidates, versions, index):
    """Prove every subsequent version instead of treating historical ACTIVE as current head."""
    skill=release['skillId'];start=release['version'];end=release.get('runtimeVersion')
    if not isinstance(end,int) or end<=start:return None
    rows={v['version']:v for v in versions if v['skillId']==skill}
    previous=rows.get(start)
    if not previous or previous['candidateId']!=release['candidateId'] or previous['skillHash']!=release['skillHash'] or previous['packageHash']!=release['packageHash']:return None
    chain=[]
    for number in range(start+1,end+1):
        current=rows.get(number)
        if not current or current['sourceType']!='SKILL_EVOLVER' or current['bodyHash']!=current['storedBodyHash']:return None
        matches=[r for r in releases if r['candidateId']==current['candidateId'] and r['skillId']==skill and r['version']==number
                 and r['status']=='ACTIVE' and r['canaryPercent']==0 and r['skillHash']==current['skillHash'] and r['packageHash']==current['packageHash']]
        candidate=next((c for c in candidates if c['candidateId']==current['candidateId']),{})
        if len(matches)!=1 or candidate.get('status')!='ACTIVE' or candidate.get('baseVersion')!=previous['version'] or candidate.get('baseHash')!=previous['skillHash']:return None
        chain.append(matches[0]['releaseId']);previous=current
    if previous['skillHash']!=release.get('runtimeHash') or previous['packageHash']!=release.get('runtimePackage'):return None
    if not any(g['generationId']==release.get('runtimeGenerationId') and g['status']=='READY' and g['dimension']==1024
               and g['version']==end and g['skillId']==skill and g['hash']==previous['skillHash'] and g['packageHash']==previous['packageHash'] for g in index):return None
    return chain


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
          'runtimeVersion',p.skill_version,'runtimeHash',p.skill_hash,'runtimePackage',p.package_hash,'runtimeGenerationId',p.generation_id)
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
        'candidates': "SELECT JSON_OBJECT('candidateId',c.candidate_id,'status',c.status,'reason',c.reason_code,'baseVersion',c.base_skill_version,'baseHash',c.base_skill_hash,'versionCount',(SELECT count(*) FROM ai_ops_skill_version v WHERE v.project_id=c.project_id AND v.evolution_job_id=c.candidate_id)) FROM ai_ops_skill_patch_candidate c WHERE c.project_id=" + scope,
    }
    queries['versions'] = "SELECT JSON_OBJECT('skillId',skill_id,'version',version,'sourceType',source_type,'candidateId',evolution_job_id,'skillHash',skill_hash,'packageHash',package_hash,'bodyHash',SHA2(content,256),'storedBodyHash',JSON_UNQUOTE(JSON_EXTRACT(artifact_hashes_json,'$.\"SKILL.md\"'))) FROM ai_ops_skill_version WHERE scope='PROJECT' AND project_id="+scope+" ORDER BY skill_id,version"
    result = {name: runtime['rows'](sql) for name, sql in queries.items()}
    automatic = [r for r in result['releases'] if json.loads(r['metadata'] or '{}').get('publicationPolicy') == 'source-qualified-auto-v1']
    pg_scope="'"+project.replace("'","''")+"'"
    pg="SELECT json_build_object('generationId',g.generation_id,'skillId',g.skill_id,'version',g.skill_version,'hash',g.skill_hash,'packageHash',g.package_hash,'status',g.status,'dimension',vector_dims(d.embedding)) FROM ops_skill_route_generation g LEFT JOIN ops_skill_route_document d ON d.generation_id=g.generation_id WHERE g.project_id="+pg_scope+" ORDER BY g.generation_id"
    raw=subprocess.check_output(['docker','exec','-i','orbisops-acceptance-pgvector-1','sh','-c',
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1'],input=pg+';',text=True)
    result['indexGenerations']=[json.loads(line) for line in raw.splitlines() if line]
    mismatches=[]
    closed_before_commit=[]
    superseded=[]
    for release in automatic:
        candidate=release['candidateId']; metadata=json.loads(release['metadata'] or '{}')
        valid=release['canaryPercent']==0
        if metadata.get('atomicPlan'):
            groups=[g for g in result['atomicGroups'] if g['candidateId']==candidate]
            members=[m for m in result['atomicMembers'] if m['candidateId']==candidate]
            candidate_row=next((c for c in result['candidates'] if c['candidateId']==candidate), {})
            if closed_before_atomic_publication(release,candidate_row,groups,members):
                closed_before_commit.append(release['releaseId'])
                continue
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
            current=release['version']==release['runtimeVersion'] and release['skillHash']==release['runtimeHash'] and release['packageHash']==release['runtimePackage']
            chain=None if current else superseding_publication_chain(release,automatic,result['candidates'],result['versions'],result['indexGenerations'])
            valid=valid and (current or bool(chain))
            if valid and chain:superseded.append({'releaseId':release['releaseId'],'subsequentPublications':chain})
        if not valid:mismatches.append(release['releaseId'])
    result.update(status='NOT_EXERCISED' if not automatic else 'FAIL' if mismatches else 'OBSERVED',
                  automaticReleaseCount=len(automatic), mismatches=mismatches,
                  closedBeforeAtomicCommit=closed_before_commit,
                  supersededByVerifiedPublicationChain=superseded,
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
