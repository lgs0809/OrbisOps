#!/usr/bin/env python3
"""Verify actual project/role denial and SELECT-only business DB accounts."""
import argparse
import json
import os
from pathlib import Path
import runpy
import subprocess

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    runtime=runpy.run_path(str(ROOT/'scripts/test-business-workflow-runtime.py'))
    support=runpy.run_path(str(ROOT/'scripts/seed-local-acceptance.py'))
    accounts=json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text())
    viewer=support['login'](accounts['ops_acceptance_viewer'])
    outsider=support['login'](accounts['ops_acceptance_b_member'])
    api=runtime['api']; token=runtime['token']; result={'result':'FAIL','checks':{}}
    path='/api/v1/admin/ops/projects/ops-acceptance-a'
    policy=next(p for p in api(path+'/mcp-tool-policies',token=token) if p['toolName']=='metrics_window')
    cases=[('viewerCannotApprove',path+'/mcp-tool-policies/'+policy['policyId']+'/approve','POST',{},viewer),
           ('projectBCannotReadAQueries',path+'/mcp-tool-calls','GET',None,outsider)]
    for name,endpoint,method,body,principal in cases:
        response=api(endpoint,method,body,principal,denied=True)
        result['checks'][name]=response
        assert response['httpStatus']==403,response
    after=next(p for p in api(path+'/mcp-tool-policies',token=token) if p['policyId']==policy['policyId'])
    assert policy==after
    result['checks']['reviewedPolicyUnchanged']={k:after[k] for k in ('policyId','toolName','schemaHash')}
    values=runtime['support']['values']
    for user,key in [('ops_acceptance_ro_a','OPS_ACCEPTANCE_DB_A_PASSWORD'),('ops_workflow_target','OPS_ACCEPTANCE_TARGET_DB_PASSWORD')]:
        command=['docker','exec','-i','-e','CHECK_DB_PASSWORD','orbisops-acceptance-mysql-1','sh','-c',
                 'MYSQL_PWD="$CHECK_DB_PASSWORD" exec mysql -h 127.0.0.1 -u '+user+' ops_acceptance_business_a']
        process=subprocess.run(command,input="UPDATE acceptance_order SET amount=amount WHERE order_id='ops-acc-a-order-1';",
                               text=True,capture_output=True,env={**os.environ,'CHECK_DB_PASSWORD':values[key]})
        result['checks'][user+'WriteDenied']={'exitCode':process.returncode,'error':process.stderr.strip()}
        assert process.returncode and 'ERROR 1142' in process.stderr
    result['result']='PASS'
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__':
    main()
