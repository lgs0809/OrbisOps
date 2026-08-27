#!/usr/bin/env python3
"""Read actual versioned content corrections and their API projection. Does not repair or publish anything."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy

ROOT=Path(__file__).resolve().parents[1]
def digest(text):return hashlib.sha256(text.encode()).hexdigest()
def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))
def inspect(candidate,output):
    if not re.fullmatch(r'skill-candidate-[a-z0-9-]+',candidate):raise ValueError('Use an actual candidate ID')
    if output.exists() or output.with_suffix('.sql').exists():raise ValueError('Retain earlier evidence')
    h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));q=h['quoted'](candidate)
    queries={
      'candidate':"SELECT JSON_OBJECT('projectId',project_id,'hash',candidate_hash,'status',status,'patchType',patch_type,'targetSkillId',target_skill_id,'risk',risk_level,'changes',CAST(changes_json AS JSON),'artifacts',CAST(artifacts_json AS JSON),'evalCases',CAST(eval_cases_json AS JSON)) FROM ai_ops_skill_patch_candidate WHERE candidate_id="+q,
      'proposal':"SELECT JSON_OBJECT('planId',plan_id,'planHash',plan_hash,'actualPlanHash',SHA2(input_json,256),'authoredHash',authored_hash,'actualAuthoredHash',SHA2(authored_json,256)) FROM ai_ops_skill_evolution_proposal WHERE candidate_id="+q,
      'revisions':"SELECT JSON_OBJECT('round',revision_round,'planId',plan_id,'planHash',plan_hash,'previousHash',previous_candidate_hash,'previousCandidate',CAST(previous_candidate_json AS JSON),'previousAuthored',previous_authored_json,'previousAuthoredHash',previous_authored_hash,'review',CAST(review_json AS JSON),'status',status,'authored',authored_json,'authoredHash',authored_hash,'revisedHash',revised_candidate_hash,'reasonCode',reason_code) FROM ai_ops_skill_content_revision WHERE candidate_id="+q+" ORDER BY revision_round"
    }
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n'+';\n'.join(queries.values())+';\nROLLBACK;\n')
    data={k:h['rows'](query) for k,query in queries.items()};checks={};result={'status':'FAIL','candidateId':candidate,'checks':checks}
    try:
        assert len(data['candidate'])==len(data['proposal'])==1
        current=data['candidate'][0];plan=data['proposal'][0];rows=data['revisions']
        checks['actual_revision_exercised']=bool(rows)
        checks['frozen_input_and_latest_author_hashes_match']=plan['planHash']==plan['actualPlanHash'] and plan['authoredHash']==plan['actualAuthoredHash']
        checks['two_round_cap_and_contiguous_archive']=len(rows)<=2 and [r['round'] for r in rows]==list(range(1,len(rows)+1))
        valid=True;last_hash=rows[0]['previousHash'] if rows else current['hash'];last_authored=rows[0]['previousAuthoredHash'] if rows else plan['authoredHash'];observations=[]
        for row in rows:
            previous=row['previousCandidate'];review=row['review'];actual_review=review['contentReview']['review']
            valid=valid and row['previousHash']==last_hash==previous['candidateHash']
            valid=valid and row['previousAuthoredHash']==last_authored==digest(row['previousAuthored'])
            valid=valid and row['planId']==plan['planId'] and row['planHash']==plan['planHash']
            valid=valid and review['contentReview']['candidateHash']==last_hash and actual_review['decision']=='BLOCK'
            valid=valid and actual_review['authoringModel']=='gpt-6.1-terra'
            valid=valid and review['failures']==['POLICY_CONTENT_REVIEW:'+reason for reason in actual_review['reasons']]
            if row['status'] in ('APPLIED','REJECTED'):
                authored=json.loads(row['authored']);valid=valid and digest(row['authored'])==row['authoredHash']
                valid=valid and authored['authoringModel']=='gpt-6.1-terra' and authored['authoringSource']=='LLM'
                if row['status']=='APPLIED':
                    expected=digest(canonical(dict(previousCandidateHash=last_hash,planHash=plan['planHash'],round=row['round'],authored=authored)))
                    valid=valid and row['revisedHash']==expected
                    valid=valid and authored['patchType']==previous['patchType'] and authored['targetSkillId']==previous['targetSkillId'] and authored['riskLevel']==previous['riskLevel']
                    last_hash=row['revisedHash'];last_authored=row['authoredHash']
            observations.append({k:row[k] for k in ['round','status','previousHash','previousAuthoredHash','authoredHash','revisedHash','reasonCode']})
        checks['all_originals_and_completed_corrections_are_hash_linked']=valid
        checks['current_projection_matches_last_applied_revision']=current['hash']==last_hash and plan['authoredHash']==last_authored
        path='/api/v1/admin/ops/skill-evolver/candidates/'+candidate+'/content-revisions?projectId='+current['projectId']
        projection=h['api'](path,token=h['token'])
        checks['normal_api_matches_native_rounds_and_current_status']=projection['candidateStatus']==current['status'] and projection['maxRounds']==2 and [(r['round'],r['status']) for r in projection['revisions']]==[(r['round'],r['status']) for r in rows]
        users=json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text());permissions={}
        for account in ['ops_acceptance_viewer','ops_acceptance_b_member','ops_acceptance_operator']:
            permissions[account]=h['api'](path,token=h['api_module']['login'](users[account]),denied=True)['httpStatus']
        checks['non_admin_roles_denied']=all(code==403 for code in permissions.values())
        result.update(status='PASS_NATIVE_REVISION_ARCHIVE' if all(checks.values()) else 'FAIL',
            candidateStatus=current['status'],rounds=observations,projection=projection,permissions=permissions,
            boundary='Actual native revision history and safe API projection only. Pending work, publication/index readiness, Skill reuse and rollback require separate evidence.')
    finally:output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False));return all(checks.values())
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    raise SystemExit(0 if inspect(a.candidate,a.output) else 1)
