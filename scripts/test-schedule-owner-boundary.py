#!/usr/bin/env python3
"""Verify a waiting schedule Run inherits its creator and rejects an unrelated approver."""
import argparse,json,re,runpy,urllib.request,urllib.error
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run-id',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if not re.fullmatch(r'task_[0-9]+_[0-9]+',a.run_id):p.error('Invalid schedule Run ID')
if a.output.exists():p.error('Output exists')
root=Path(__file__).resolve().parents[1]
h=runpy.run_path(str(root/'scripts/test-mcp-runtime.py'))
q="SELECT JSON_OBJECT('runId',r.run_id,'owner',r.user_id,'createdBy',s.created_by,'status',r.status) FROM ai_ops_agent_run r JOIN ai_agent_task_schedule s ON s.id="+a.run_id.split('_')[1]+" WHERE r.run_id="+h['quoted'](a.run_id)
owner=h['rows'](q)[0];assert owner['owner']==owner['createdBy'] and owner['status']=='WAITING_APPROVAL',owner
approvalq="SELECT JSON_OBJECT('approvalId',approval_id,'status',status,'decidedBy',decided_by) FROM ai_ops_workflow_approval WHERE run_id="+h['quoted'](a.run_id)+" ORDER BY requested_at"
before=h['rows'](approvalq);waiting=next(x for x in before if x['status']=='WAITING')
login=h['api_module']['login'];other=login(json.loads((root/'deploy/.acceptance-private/landing-reviewer.json').read_text()))
req=urllib.request.Request('http://127.0.0.1:18089/api/v1/agent/chat/runs/'+a.run_id+'/workflow-approval/decision?projectId=ops-acceptance-a',data=json.dumps({'approvalId':waiting['approvalId'],'decision':'APPROVE'}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+other})
try:
 urllib.request.urlopen(req,timeout=20);raise AssertionError('Unrelated approver accepted')
except urllib.error.HTTPError as e:
 assert e.code==403,e.code
 response=json.loads(e.read())
after=h['rows'](approvalq);assert before==after
result={'status':'PASS','ownership':owner,'unrelatedApproverHttpStatus':403,'approvalsUnchanged':True,'response':response}
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');a.output.with_suffix('.sql').write_text(q+';\n'+approvalq+';\n');print(json.dumps(result,ensure_ascii=False))
