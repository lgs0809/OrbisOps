#!/usr/bin/env python3
"""Continue a real timing observation in its original session; retain old insufficiency.

Only normal natural-language message/draft/verification APIs are used. Exact
20 native durations are independently compared with SQLite. This is one logical
source retry, not a new independent fault mechanism or source.
"""
import argparse,datetime as dt,hashlib,importlib.util,json,runpy,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-closure-business-a'

def main(prior,output,existing_execution=None):
 if output.exists():raise ValueError('Retain previous evidence')
 h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));api,token,q=h['api'],h['token'],h['quoted']
 old=json.loads(prior.read_text());previous=old['results'][0];definition=previous['definition'];service=previous['service'];delay=definition['targetSeed']['state']['configuration']['delayMs']
 if previous['status']!='DRAFT_NOT_READY' or delay<=0:raise ValueError('Existing positive-delay insufficient observation required')
 jar=subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
 report={'status':'STARTING_NATIVE_CONTINUATION','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'priorEvidence':str(prior),'priorSha256':hashlib.sha256(prior.read_bytes()).hexdigest(),'priorRunId':previous['runId'],'sessionId':previous['sessionId'],'service':service,'newIndependentSources':0,'logicalSourceRetry':True,'deployedJarSha256':jar,'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
 def save():output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 def until(check,seconds):
  deadline=time.monotonic()+seconds
  while time.monotonic()<deadline:
   result=check()
   if result:return result
   time.sleep(2)
  raise TimeoutError('Actual native state remains pending')
 save()
 try:
  query=f'继续刚才同一项资源观察验收，保留上一轮缺少真实耗时的证据不足结论。请仅只读重新观察项目{PROJECT}、test服务{service}，按本轮实际开始至结束区间保留原来的全部身份、版本、epoch、配置摘要、20次GET /orders全部HTTP200和错误0条件，并核对前后配置delayMs均为{delay}、每个实际订单请求的原生计时不少于{delay}毫秒、20条总计时不少于{20*delay}毫秒。新窗口另记，不冒称给旧窗口补造耗时。执行结束后仍等待业务验收。'
  report.update(status='CONTINUATION_REQUESTED_RESPONSE_UNCONFIRMED',naturalUserQuery=query);save()
  if existing_execution:
   retained=json.loads(existing_execution.read_text())
   if retained.get('priorSha256')!=report['priorSha256'] or retained.get('sessionId')!=previous['sessionId'] or retained.get('service')!=service:raise ValueError('Exact retained continuation identity required')
   execution_proof_path=Path(retained['truthEvidence']);execution_proof=json.loads(execution_proof_path.read_text())
   if execution_proof.get('status')!='PASS' or execution_proof.get('run',{}).get('runId')!=retained['runId'] or execution_proof.get('deployedJarSha256')!=retained['deployedJarSha256']:
    raise ValueError('Retained execution receipt/artifact binding is not proven')
   run=report['runId']=retained['runId'];report.update(newWorkflowExecutions=0,retainedExecutionEvidence=str(existing_execution),retainedExecutionEvidenceSha256=hashlib.sha256(existing_execution.read_bytes()).hexdigest(),retainedHostOutcome=retained['status'],executionJarSha256=retained['deployedJarSha256'],retainedExecutionProofSha256=hashlib.sha256(execution_proof_path.read_bytes()).hexdigest(),acceptanceRetryCrossesDeployment=jar!=retained['deployedJarSha256'])
  else:
   response=api('/api/v1/user/chat/sessions/'+previous['sessionId']+'/messages','POST',{'projectId':PROJECT,'query':query,'mode':'AGENT','engine':'GRAPH','agentDefinitionId':definition['agentId'],'agentVersion':definition['version'],'metadata':{'executionType':'WORKFLOW'}},token,timeout=360)
   run=report['runId']=response['metadata']['runId'];report['newWorkflowExecutions']=1
  save()
  def ended():
   v=h['facts'](run);return v if v['status'] in ['SUCCEEDED','FAILED','CANCELED'] else None
  report['nativeRun']=until(ended,480)
  if report['nativeRun']['status']!='SUCCEEDED':raise ValueError('Actual continued workflow did not succeed')
  spec=importlib.util.spec_from_file_location('truth',ROOT/'scripts/inspect-closure-observation.py');truth=importlib.util.module_from_spec(spec);spec.loader.exec_module(truth)
  proof_path=output.with_name(output.stem+'-proof.json');proof=truth.inspect(run,service,proof_path);report['truthEvidence']=str(proof_path)
  if proof['status']!='PASS' or proof['deployedJarSha256']!=jar:raise ValueError('Actual native durations and artifact are not proven')
  if existing_execution:
   def receipt_identity(v):return sorted((r['resultId'],r['hash'],r['status']) for r in v['receipts'])
   report['retainedExecutionReceiptsUnchanged']=receipt_identity(proof)==receipt_identity(execution_proof) and proof['run']==execution_proof['run']
   if not report['retainedExecutionReceiptsUnchanged']:raise ValueError('Retained native execution changed after deployment')
  else:report['executionJarSha256']=jar
  observation=next(r for r in proof['receipts'] if 'beforeState' in json.loads(r['output']).get('normalizedContent',{}));content=json.loads(observation['output'])['normalizedContent']
  required={pointer:('EQ',content[key]) for pointer,key in [('/projectId','projectId'),('/environment','environment'),('/resourceKey','resourceKey'),('/status','status'),('/observationStart','observationStart'),('/observationEnd','observationEnd'),('/configurationUnchanged','configurationUnchanged'),('/requestCount','requestCount'),('/errorCount','errorCount'),('/allRequestsSucceeded','allRequestsSucceeded'),('/requestMethod','requestMethod'),('/routeDefinition','routeDefinition')]}
  for phase in ['beforeState','afterState']:
   for field in ['version','epoch','configurationDigest']:required['/'+phase+'/'+field]=('EQ',content[phase][field])
  configured={'delayMs':delay,'failEvery':0};digest=hashlib.sha256(json.dumps(configured,sort_keys=True,separators=(',',':')).encode()).hexdigest()
  if any(content[phase]['configuration']!=configured or content[phase]['configurationDigest']!=digest for phase in ['beforeState','afterState']):raise ValueError('Exact original positive-delay configuration not observed')
  required['/minimumRequestDurationMs']=('GE',delay);required['/totalRequestDurationMs']=('GE',20*delay)
  def assigned():
   rows=h['rows']("SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id WHERE t.project_id="+q(PROJECT)+" AND t.status='ASSIGNED' AND t.source_run_ref="+q(run));return rows[0] if len(rows)==1 else None
  report.update(status='WAITING_NATIVE_EPISODE',requiredBusinessFields=required);save();episode=report['episode']=until(assigned,900)
  address='/api/v1/user/ops/task-acceptance/'+episode['episodeId'];suffix='?projectId='+PROJECT
  review=f'保留同一资源观察任务与所有原始要求：项目{PROJECT}、test、service://{service}/test、本轮实际observationStart至observationEnd、PASSED、20次真实GET /orders全部HTTP200和错误0、前后observation-initial、epoch0、配置摘要一致及configurationUnchanged true。前后完整配置均为delayMs={delay}、failEvery=0；原生configurationDigest按该确切对象的排序JSON重算为{digest}，两个configurationDigest EQ检查同时绑定整个配置（包含delayMs条件），不要再重复同一配置的单字段断言以挤掉原始其他要求。用minimumRequestDurationMs GE {delay}与totalRequestDurationMs GE {20*delay}核验全部20条实际原生计时；它们是已持久化traceId逐条真实耗时重算的最小值与总和，独立SQLite交叉核对已保留。用已有18项完整身份/区间/版本/epoch/摘要/业务成功字段加这2项计时共20项，goalReview明确上述配置与真实耗时含义；不能缩窄目标、不能把旧失败改为通过，原运行成功不等于验收。'
  report.update(status='DRAFT_REQUESTED',reviewerInstruction=review);save();draft=report['draft']=api(address+'/draft'+suffix,'POST',{'revision':episode['revision'],'instruction':review},token,timeout=720);save()
  if draft['status']!='READY':report['status']='DRAFT_NOT_READY';return
  criteria=draft['request']['criteria'];report['criteriaReview']={pointer:any(c['pointer']==pointer and c['operator']==operator and c['expected']==value and c['resultId']==observation['resultId'] and c['outputHash']==observation['hash'] for c in criteria) for pointer,(operator,value) in required.items()}
  if not all(report['criteriaReview'].values()):report['status']='DRAFT_REQUIRES_REVIEW';return
  report['status']='CONFIRMATION_REQUESTED';save();verified=report['verification']=api(address+suffix,'POST',draft['request'],token,timeout=120);report['after']=api(address+suffix,token=token)
  report['status']='PASS_ACTUAL_ACCEPTANCE_LOGICAL_RETRY' if verified['outcome']=='SUCCEEDED' and report['after']['outcome']=='SUCCEEDED' and all(c['verdict']=='PASSED' for c in verified['checks']) else 'BUSINESS_VERIFICATION_FAILED'
 except Exception as e:
  report.update(interruptedStage=report['status'],status='FAILED_OR_UNCONFIRMED',error=type(e).__name__+': '+str(e));raise
 finally:
  report['finishedAt']=dt.datetime.now(dt.timezone.utc).isoformat();save();output.with_suffix('.sql').write_text('\n'.join(h['sql_queries'])+'\n');print(json.dumps({'status':report['status'],'runId':report.get('runId')}),flush=True)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prior',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--existing-execution',type=Path);a=p.parse_args();main(a.prior,a.output,a.existing_execution)
