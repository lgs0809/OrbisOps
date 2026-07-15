#!/usr/bin/env python3
"""Read-only SQL evidence for a synthetic schedule approval run. Never changes outcomes."""
import argparse
import json
from pathlib import Path
import re
import subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--run-id',required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if not re.fullmatch(r'task_[0-9]+_[0-9]+',a.run_id): p.error('Expected task_<schedule>_<execution>')
if a.output.exists(): p.error('Output exists; preserving it')
_,schedule,execution=a.run_id.split('_')
q=f"""SELECT JSON_OBJECT('executionId',id,'scheduleId',schedule_id,'status',status,'endedAt',ended_at,'error',error_message) FROM ai_agent_task_execution WHERE id={execution} AND schedule_id={schedule};
SELECT JSON_OBJECT('runId',run_id,'status',status,'error',error_message,'leaseExpiresAt',lease_expires_at) FROM ai_ops_agent_run WHERE run_id='{a.run_id}';
SELECT JSON_OBJECT('approvalId',approval_id,'nodeId',node_id,'status',status,'decidedBy',decided_by) FROM ai_ops_workflow_approval WHERE run_id='{a.run_id}' ORDER BY requested_at;
SELECT JSON_OBJECT('checkpoints',COUNT(*)) FROM ai_ops_agent_run_checkpoint WHERE run_id='{a.run_id}';
"""
r=subprocess.run(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B --raw orbisops_acceptance'],input=q,text=True,capture_output=True,check=True)
rows=[json.loads(x) for x in r.stdout.splitlines() if x]
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps({'scope':'READ_ONLY_SYNTHETIC_SCHEDULE_APPROVAL','rows':rows},ensure_ascii=False,indent=2)+'\n')
a.output.with_suffix('.sql').write_text(q)
print(json.dumps(rows,ensure_ascii=False))
