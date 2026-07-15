#!/usr/bin/env python3
"""Recover missing schedule creators only from unambiguous creation audit evidence.
Defaults to read-only inspection. --apply fills NULL/empty creators, never Run or approval states.
Preserves legacy rows without a unique matching creation audit and reports them unresolved.
"""
import argparse,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apply',action='store_true');p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists(): p.error('Output exists; preserving evidence')
def sql(q):
 r=subprocess.run(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c',
   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw orbisops_acceptance'],
   input=q,text=True,capture_output=True,check=True)
 return [json.loads(x) for x in r.stdout.splitlines() if x.startswith('{')]
# Exact project/name/agent and creation timestamp. Multiple matches remain unresolved.
match="""FROM ai_agent_task_schedule s JOIN ai_ops_config_audit a
 ON a.project_id=s.project_id AND a.target_id=s.task_name
 AND a.module_name='task-schedule' AND a.action_name='create' AND a.result_status='SUCCESS'
 AND a.agent_id=s.agent_id AND a.operator_id<>''
 AND ABS(TIMESTAMPDIFF(SECOND,s.create_time,a.create_time))<=2
 WHERE COALESCE(s.created_by,'')=''"""
query="SELECT JSON_OBJECT('scheduleId',s.id,'creator',MIN(a.operator_id),'auditId',MIN(a.audit_id),'matches',COUNT(*)) "+match+" GROUP BY s.id;"
before=sql(query)
mutation="""UPDATE ai_agent_task_schedule s JOIN (
 SELECT schedule_id,creator FROM (
 SELECT s.id schedule_id,MIN(a.operator_id) creator """+match+"""
 GROUP BY s.id HAVING COUNT(*)=1
 ) proven
) evidence ON evidence.schedule_id=s.id
 SET s.created_by=evidence.creator WHERE COALESCE(s.created_by,'')='';"""
if a.apply: sql(mutation)
inspection="SELECT JSON_OBJECT('scheduleId',id,'projectId',project_id,'createdBy',created_by,'status',status) FROM ai_agent_task_schedule ORDER BY id;"
after=sql(inspection)
a.output.write_text(json.dumps({'applied':a.apply,'creationEvidence':before,'schedules':after},ensure_ascii=False,indent=2)+'\n')
a.output.with_suffix('.sql').write_text(query+'\n'+('-- Applied explicit ownership repair; does not alter execution outcomes.\n'+mutation+'\n' if a.apply else '')+inspection+'\n')
print(json.dumps({'applied':a.apply,'proven':sum(x['matches']==1 for x in before),'unresolved':sum(not x['createdBy'] for x in after)}))
