#!/usr/bin/env python3
"""Real read-only MCP scope-alias comparison. Writes evidence, never target configuration."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import time
import uuid
ROOT=Path(__file__).resolve().parents[1]

def main(output):
    h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py')); api=h['api'];token=h['token'];q=h['quoted'];queries=[]
    actor=next(u for u in api('/api/v1/admin/admin-user/query-all',token=token) if u['username']==json.loads((ROOT/'deploy/.acceptance-private/admin.json').read_text())['username'])
    connections=api('/api/v1/admin/ops/projects/ops-acceptance-a/tools',token=token)
    mcp=next(c for c in connections if c.get('transportConfig',{}).get('endpoint')=='http://127.0.0.1:8281/mcp')
    def rows(sql):
        queries.append(sql);return h['rows'](sql)
    def state():
        return rows("SELECT JSON_OBJECT('version',version,'scenario',scenario,'receipts',(SELECT COUNT(*) FROM ops_acceptance_business_a.ops08_deployment_receipt)) FROM ops_acceptance_business_a.acceptance_service WHERE service_id='ops-acc-a-service-2'")
    proof={'state':'NOT_PASSED','scope':'REAL_READONLY_SCOPE_MAPPING','calls':[]};output.parent.mkdir(parents=True,exist_ok=True)
    end=int(time.time())-15;start=end-300;observed={}
    try:
        proof['before']=state()
        for environment in ('acceptance','prod'):
            for name in ('metrics_window','target_version'):
                run='scope-alias-'+uuid.uuid4().hex
                window={'projectId':'ops-acceptance-a','serviceId':'ops-acc-a-service-2','environment':environment,'startEpoch':start,'endEpoch':end}
                api('/api/v1/admin/ops/tool-executions','POST',{'projectId':'ops-acceptance-a','runId':run,'userId':actor['userId'],'authenticatedUsername':actor['username'],
                    'toolsetId':'mcp.'+mcp['mcpId'],'toolName':name,'requireReadOnly':True,'executionScope':'PRE_APPROVAL_WORKFLOW','idempotencyKey':run,'arguments':{'window':window}},token)
                receipts=rows("SELECT JSON_OBJECT('resultId',result_id,'output',full_output,'hash',output_hash) FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id="+q(run))
                assert len(receipts)==1
                receipt=receipts[0];assert hashlib.sha256(receipt['output'].encode()).hexdigest()==receipt['hash']
                native=json.loads(receipt['output'])['normalizedContent'];assert native['status']=='AVAILABLE' and native['scope']==window
                proof['calls'].append({'runId':run,'tool':name,'environment':environment,'receipt':receipt})
                observed[environment,name]=native
        old=observed['acceptance','metrics_window'];new=observed['prod','metrics_window']
        assert old['series'] and len(old['series'])==len(new['series'])
        business=0
        for a,b in zip(old['series'],new['series']):
            assert a['values']==b['values']
            if a['metric'].get('__name__')=='up':assert a==b
            else:
                business+=1;assert b['sourceMetric']==a['metric']
                assert b['metric']=={**a['metric'],'environment':'prod'}
        assert business>0
        assert new['scopeMapping']['sourceEnvironment']=='acceptance'
        assert new['scopeMapping']['sourceTarget']=='workflow-target:8280'
        assert observed['acceptance','target_version']['version']==observed['prod','target_version']['version']==proof['before'][0]['version']
        assert observed['prod','target_version']['resourceIdentity']=='service://ops-acc-a-service-2/prod'
        proof['after']=state();assert proof['before']==proof['after']
        proof.update(state='PASS',realMcpCalls=4,unchangedMetricSeries=len(old['series']),businessSeries=business)
    finally:
        output.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n');output.with_suffix('.sql').write_text(';\n'.join(queries)+';\n')
    print(json.dumps({k:v for k,v in proof.items() if k not in ('calls','before','after')}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);main(p.parse_args().output)
