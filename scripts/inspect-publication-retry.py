#!/usr/bin/env python3
"""Verify a retained candidate's normal publication queue, duplicate requests and role boundaries.
Does not seed approvals, accepted sources, candidate content, review results or publication state.
"""
import argparse,json,runpy,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def inspect(candidate,project,output):
    if output.exists() or output.with_suffix('.sql').exists():raise ValueError('Preserve earlier evidence')
    r=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));q=r['quoted'](candidate)
    prefix='/api/v1/admin/ops/skill-evolver/candidates/'+candidate+'/publication'
    query="SELECT JSON_OBJECT('candidateId',candidate_id,'status',status,'attempts',attempts,'nextRunAt',next_run_at,'reason',last_reason) FROM ai_ops_skill_publication_retry WHERE candidate_id="+q
    review="SELECT JSON_OBJECT('status',status,'result',CAST(result_json AS JSON)) FROM ai_ops_skill_patch_validation WHERE validation_type='CONTENT_REVIEW' AND candidate_id="+q
    evidence={'boundary':'Queue/access/review facts; complete Skill execution acceptance is separate.'}
    output.parent.mkdir(parents=True,exist_ok=True);output.with_suffix('.sql').write_text(query+';\n'+review+';\n')
    try:
        before=r['rows'](query);assert len(before)==1,'Enqueue through the normal candidate action first'
        started=time.monotonic()
        first=r['api'](prefix+'/retry','POST',{'projectId':project},r['token'])
        second=r['api'](prefix+'/retry','POST',{'projectId':project},r['token'])
        evidence['duplicateSeconds']=round(time.monotonic()-started,3)
        assert first['candidate_id']==second['candidate_id']==candidate
        after=r['rows'](query);assert len(after)==1
        # Worker progress may change status; duplicate submission cannot rewind its attempt.
        assert after[0]['attempts']>=before[0]['attempts']
        evidence['queue']=after[0];evidence['review']=r['rows'](review)
        users=json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text());permissions={}
        for username in ('ops_acceptance_viewer','ops_acceptance_b_member','ops_acceptance_operator'):
            token=r['api_module']['login'](users[username])
            get=r['api'](prefix+'?projectId='+project,token=token,denied=True)
            post=r['api'](prefix+'/retry','POST',{'projectId':project},token,denied=True)
            assert get['httpStatus']==403 and post['httpStatus']==403
            permissions[username]={'read':403,'enqueue':403}
        evidence['permissions']=permissions
        evidence['publication']=r['api'](prefix+'?projectId='+project,token=r['token'])
        evidence['status']='PASS_QUEUE_IDEMPOTENCY_AND_PERMISSIONS'
    except Exception as error:
        evidence.update(status='FAIL',failureType=type(error).__name__);raise
    finally:output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:evidence[k] for k in ('status','duplicateSeconds','publication')},ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-id',required=True);p.add_argument('--project',default='ops-acceptance-a');p.add_argument('--output',required=True,type=Path)
    a=p.parse_args();inspect(a.candidate_id,a.project,a.output)
