#!/usr/bin/env python3
"""Reject reuse of an earlier approval while a later node is waiting. Uses real APIs and read-only SQL."""
import argparse,json,re,runpy,urllib.request,urllib.error
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run-id',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_-]+',a.run_id): p.error('Invalid run ID')
if a.output.exists(): p.error('Output exists')
h=runpy.run_path(str(Path(__file__).resolve().parent/'test-mcp-runtime.py'))
q="SELECT JSON_OBJECT('approvalId',approval_id,'nodeId',node_id,'status',status) FROM ai_ops_workflow_approval WHERE project_id='ops-acceptance-a' AND run_id="+h['quoted'](a.run_id)+" ORDER BY requested_at"
before=h['rows'](q)
assert len(before)==2 and [x['status'] for x in before]==['APPROVED','WAITING'],before
req=urllib.request.Request('http://127.0.0.1:18089/api/v1/agent/chat/runs/'+a.run_id+'/workflow-approval/decision?projectId=ops-acceptance-a',data=json.dumps({'approvalId':before[0]['approvalId'],'decision':'APPROVE'}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+h['token']})
try:
    urllib.request.urlopen(req,timeout=30)
    raise AssertionError('Stale approval was accepted')
except urllib.error.HTTPError as e:
    status=e.code;assert status==403,status
    response=json.loads(e.read())
after=h['rows'](q);assert before==after,'Stale request changed approvals'
a.output.write_text(json.dumps({'status':'PASS','runId':a.run_id,'httpStatus':status,'before':before,'after':after,'response':response},ensure_ascii=False,indent=2)+'\n')
a.output.with_suffix('.sql').write_text(q+';\n');print(json.dumps({'status':'PASS','httpStatus':status,'approvalsUnchanged':True}))
