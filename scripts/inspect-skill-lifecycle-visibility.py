#!/usr/bin/env python3
"""Retain before/after evidence of actual effective methods and the old failed unpublished merge; no writes."""
import argparse,json
from pathlib import Path
from deployed_repository_probe import run_probe


def inspect(output,verify):
    if output.exists() or output.with_suffix('.sql').exists():raise ValueError('Retain earlier evidence')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.sql').write_text("""START TRANSACTION READ ONLY;
SELECT release_id,status,reason_code,released_version FROM ai_ops_skill_release
 WHERE candidate_id='skill-candidate-6242fe80-03ca-4e67-a4bd-d96a64c5b2fe' AND project_id='ops-acceptance-a';
SELECT candidate_id,status,candidate_hash FROM ai_ops_skill_patch_candidate
 WHERE candidate_id='skill-candidate-6242fe80-03ca-4e67-a4bd-d96a64c5b2fe' AND project_id='ops-acceptance-a';
SELECT plan_id,plan_hash,SHA2(input_json,256) AS actual_plan_hash,authored_hash,SHA2(authored_json,256) AS actual_authored_hash
 FROM ai_ops_skill_evolution_proposal WHERE candidate_id='skill-candidate-6242fe80-03ca-4e67-a4bd-d96a64c5b2fe';
SELECT m.skill_id,m.role,g.status FROM ai_ops_skill_atomic_member m JOIN ai_ops_skill_atomic_publication g USING(candidate_id)
 WHERE m.project_id='ops-acceptance-a';
ROLLBACK;
""")
    result={'status':'FAIL'}
    try:result.update(run_probe('InspectSkillLifecycleVisibility',json.dumps({'verifyClosed':verify})))
    except Exception as failure:
        result['diagnostic']=str(failure)[:2500];result['failureType']=type(failure).__name__;raise
    finally:output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':result['status'],'release':result['release']['status'],'candidate':result['candidate']['status']}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--verify-closed',action='store_true')
    a=p.parse_args();inspect(a.output,a.verify_closed)
