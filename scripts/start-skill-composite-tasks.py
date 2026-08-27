#!/usr/bin/env python3
"""Run six distinct directory/order resource pairs via normal natural-language workflow messages."""
from pathlib import Path
import argparse,json,runpy,subprocess,hashlib,datetime as dt,time
from skill_composite_cases import resource_pair
ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-acceptance-a'

def start(prepared,output,expected_jar,first=1,retry_from=None,last=6):
 if output.exists():raise ValueError('Retain earlier admissions; reconcile unknown requests instead of resubmitting')
 if not 1<=first<=last<=6:raise ValueError('Choose an explicit range of resource pairs 1..6')
 setup=json.loads(prepared.read_text());definition=setup['definition'];agent=definition['agentId']
 if not any(str(n.get('mode','')).upper()=='REACT' and n.get('mcpIds') for n in definition['nodes']):
  raise ValueError('Actual tool use requires the framework bounded ReAct node, not a single-call LLM node')
 deployed=subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
 if deployed!=expected_jar:raise ValueError('Complete and deploy the intended tested backend before admitting tasks')
 h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));api,token=h['api'],h['token']
 prior=json.loads(retry_from.read_text()) if retry_from else None
 if prior and prior['definitionHash']!=definition['definitionHash']:
  raise ValueError('A retry must preserve the original published workflow binding')
 versions=api('/api/v1/admin/ops-agents/'+agent+'/versions',token=token)
 assert any(v['version']==definition['version'] and v['definitionHash']==definition['definitionHash'] and v['lifecycle']=='PUBLISHED' for v in versions)
 report={'status':'RUNNING_ACTUAL_LOCAL_RESOURCE_PAIRS','jarSha256':deployed,'definitionHash':definition['definitionHash'],'firstPair':first,'lastPair':last,'plannedPairs':last-first+1,'tasks':[],
  'scope':'Six distinct local synthetic directory/order pairs, actual MCP/SQLite/MySQL/model execution; not six production fault mechanisms or accepted outcomes.',
  'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
 if prior:report['retryFrom']={'file':str(retry_from),'sha256':hashlib.sha256(retry_from.read_bytes()).hexdigest()}
 def save():output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 save()
 for n in range(first,last+1):
  if subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]!=expected_jar:
   raise ValueError('Backend changed between admissions; inspect before submitting another task')
  directory,service,order=resource_pair(n)
  query=f'请按当前绑定方法，只读核对两项独立现状：目录服务 {directory} 当前实际运行的版本和状态；以及 acceptance 环境中服务 {service}、订单 {order} 固定订单查询的真实 MySQL EXPLAIN FORMAT=JSON 逐表访问方式和索引。分别保留各自服务、订单、逻辑环境及本次实际工具依据，不能以目录状态代替数据库计划，也不能以数据库计划证明健康。不修复、不发布、不修改数据；执行完成后等待业务验收确认。'
  if prior:
   old=next(t for t in prior['tasks'] if t['pair']==n)
   if not old.get('runId'):raise ValueError('Reconcile an unknown earlier admission before retrying')
   fact=h['rows']("SELECT JSON_OBJECT('status',status,'sessionId',session_id,'receipts',(SELECT COUNT(*) FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id=r.run_id)) FROM ai_ops_agent_run r WHERE project_id='ops-acceptance-a' AND run_id="+h['quoted'](old['runId']))
   if len(fact)!=1 or fact[0]['sessionId']!=old['sessionId'] or fact[0]['status'] not in ('SUCCEEDED','FAILED','CANCELED') or fact[0]['receipts']!=0:
    raise ValueError('Prior execution must be terminal and have no uninspected native receipts')
   session=old['sessionId']
   query='工具连接已恢复，请重新执行这项未完成的原任务。'+query
  else:
   session=api('/api/v1/user/chat/session','POST',{'projectId':PROJECT,'agentId':agent,'agentVersion':definition['version'],'title':'目录与订单联合核对 '+str(n)},token)
  task={'pair':n,'directory':directory,'service':service,'order':order,'sessionId':session,'query':query,'status':'REQUEST_SENT_UNCONFIRMED','startedAt':dt.datetime.now(dt.timezone.utc).isoformat()}
  report['tasks'].append(task);save()
  if prior:task['previousRunId']=old['runId'];task['previousObservation']=fact[0];save()
  try:
   result=api('/api/v1/user/chat/sessions/'+session+'/messages','POST',{'projectId':PROJECT,'query':query,'mode':'AGENT','engine':'GRAPH','agentDefinitionId':agent,'agentVersion':definition['version'],'metadata':{'executionType':'WORKFLOW'}},token,timeout=720)
   task.update(runId=result['metadata']['runId'],status='SUBMITTED')
  except Exception as error:
   task['failureType']=type(error).__name__;report['status']='INTERRUPTED_ADMISSION_REQUIRES_RECONCILIATION';save();raise
  save();print(json.dumps({'pair':n,'runId':task['runId'],'status':task['status']}),flush=True)
  # The normal chat response is an admission acknowledgement, not a terminal
  # execution receipt. Await the actual persisted Run before admitting another
  # resource pair or handing it to independent business acceptance.
  deadline=time.monotonic()+1800
  while time.monotonic()<deadline:
   observed=h['facts'](task['runId'])
   task['executionObservation']=observed;save()
   if observed['status'] in ('SUCCEEDED','FAILED','CANCELED'):break
   time.sleep(2)
  else:
   report['status']='PENDING_CANONICAL_EXECUTION';save();raise TimeoutError('Existing Run remains pending; do not submit another pair')
  task['status']='RUN_COMPLETED_NOT_ACCEPTED' if observed['status']=='SUCCEEDED' else 'EXECUTION_FAILED'
  save()
  if observed['status']!='SUCCEEDED':
   report['status']='INTERRUPTED_EXECUTION_REQUIRES_INSPECTION';save();raise RuntimeError('Inspect the actual failed Run before adding tasks')
 report['status']='PLANNED_TASKS_SUBMITTED_NOT_ACCEPTED';save()

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepared',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);parser.add_argument('--expected-jar',required=True);parser.add_argument('--first',type=int,default=1);parser.add_argument('--last',type=int,default=6);parser.add_argument('--retry-from',type=Path)
 a=parser.parse_args();start(a.prepared,a.output,a.expected_jar,a.first,a.retry_from,a.last)
