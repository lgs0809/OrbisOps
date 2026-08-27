#!/usr/bin/env python3
"""API fixture: authenticated creation, spoofed creator ignored, unauthorized editor is rejected, authorized edit preserves owner.
Leaves a disabled, reusable synthetic schedule; never enables cron or runs a model.
"""
import argparse,json,runpy
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():p.error('Output exists')
root=Path(__file__).resolve().parents[1];h=runpy.run_path(str(root/'scripts/test-mcp-runtime.py'))
api=h['api'];base='/api/v1/admin/task-schedule';project='ops-acceptance-a';name='OPS-02 创建者归属传递验收（合成）'
listing=lambda:api(base+'/list?projectId='+project,token=h['token'])
existing=next((x for x in listing() if x['taskName']==name),None)
if existing is None:
 source=next(x for x in listing() if x['id']==2)
 body={k:v for k,v in source.items() if k not in ('id','createTime','updateTime')}
 body.update(taskName=name,createdBy='forged-creator',status=0)
 assert api(base+'/create','POST',body,h['token']) is True
 existing=next(x for x in listing() if x['taskName']==name)
assert existing['createdBy'] and existing['createdBy']!='forged-creator' and existing['status']==0
before=existing['createdBy']
other=h['api_module']['login'](json.loads((root/'deploy/.acceptance-private/landing-reviewer.json').read_text()))
body=dict(existing);body['createdBy']='forged-editor';body['description']='合成归属验收：编辑不改变创建者，保持停用。'
try:
 api(base+'/update-by-id','PUT',body,other)
 raise AssertionError('Unauthorized editor accepted')
except RuntimeError as error:
 assert 'HTTP 403' in str(error), str(error)
assert api(base+'/update-by-id','PUT',body,h['token']) is True
after=next(x for x in listing() if x['id']==existing['id'])
assert after['createdBy']==before and after['status']==0
q="SELECT JSON_OBJECT('scheduleId',id,'createdBy',created_by,'status',status) FROM ai_agent_task_schedule WHERE id="+str(existing['id'])
rows=h['rows'](q);assert rows[0]['createdBy']==before
result={'status':'PASS','fixtureId':existing['id'],'creator':before,'spoofedCreatorIgnored':True,'unauthorizedEditDenied':True,'authorizedEditPreservedOwner':True,'rows':rows}
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');a.output.with_suffix('.sql').write_text(q+';\n');print(json.dumps(result))
