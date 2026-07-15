#!/usr/bin/env python3
"""Read-only durable retry evidence; no API login, task mutation, prompts or credentials in output."""
import argparse
import json
from pathlib import Path
import re
import subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--session-id',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9:_-]{1,80}',a.session_id):
    p.error('Invalid session ID')
if a.output.exists():
    p.error('Choose a new evidence path; existing results are preserved')
sql=f"""START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('runId',source_run_ref,'status',status,'attempts',attempts,'epoch',epoch,
 'inputHash',input_hash,'inputHashMatches',IF(input_json='',NULL,SHA2(input_json,256)=input_hash),
 'updatedAtMs',UNIX_TIMESTAMP(update_time)*1000,'nextAttemptMs',next_attempt_ms,'leaseUntilMs',lease_until_ms,'error',last_error,'episodeId',episode_id)
FROM ai_ops_task_episode_turn WHERE session_id='{a.session_id}' ORDER BY turn_seq;
COMMIT;
"""
r=subprocess.run(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c',
                  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B --raw orbisops_acceptance'],
                 input=sql,text=True,capture_output=True,check=True)
rows=[json.loads(line) for line in r.stdout.splitlines()]
assert all(row['attempts']>=0 and row['inputHashMatches'] in (1,None) for row in rows)
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(dict(scope='READ_ONLY_RETRY_SNAPSHOT',sessionId=a.session_id,turns=rows),indent=2)+'\n')
a.output.with_suffix('.sql').write_text(sql)
print(json.dumps({'turns':len(rows),'states':[(row['status'],row['attempts']) for row in rows]}))
