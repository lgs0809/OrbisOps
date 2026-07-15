#!/usr/bin/env python3
"""Normal business observation -> independent truth -> natural draft -> reviewed acceptance.

Retries the original normal sessions with fresh actual runs and keeps every in-flight/failed stage. Existing accepted
task 01 is retained as prior evidence, not counted as a new model execution. These
resource variants exercise actual source batching, never 60 independent fault cases.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import runpy
import subprocess
import time

ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-closure-business-a'

def main(prepared,prior,output,last, retry_batch):
    if output.exists():raise ValueError('Retain prior execution evidence')
    h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));api,token,q=h['api'],h['token'],h['quoted']
    spec=importlib.util.spec_from_file_location('actual_closure_truth',ROOT/'scripts/inspect-closure-observation.py');truth=importlib.util.module_from_spec(spec);spec.loader.exec_module(truth)
    old=json.loads(prior.read_text())
    original=json.loads(retry_batch.read_text())
    if original['status']=='RUNNING':raise ValueError('Original batch must naturally finish first')
    retries={int(row['service'].rsplit('-',1)[1]):row for row in original['results'] if row['status']!='PASS_ACTUAL_ACCEPTANCE'}
    if old['acceptance']['outcome']!='SUCCEEDED':raise ValueError('Prior CUA acceptance must actually succeed')
    jar=subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
    report={'status':'RUNNING','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'scope':'ACTUAL_ACCEPTED_SOURCE_BATCHING_SAME_OBSERVATION_MECHANISM_NOT_FORMAL_FAULT_BENCHMARK',
        'logicalTasksRetried':len(retries),'priorAcceptedLogicalTasks':1+sum(row['status']=='PASS_ACTUAL_ACCEPTANCE' for row in original['results']),'designedTotalLogicalTasks':last,'originalBatch':str(retry_batch),'originalBatchSha256':hashlib.sha256(retry_batch.read_bytes()).hexdigest(),'newIndependentBenchmarkSamples':0,'priorEvidence':str(prior),'priorEvidenceSha256':hashlib.sha256(prior.read_bytes()).hexdigest(),
        'deployedJarSha256':jar,'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'results':[]}
    def save():output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    def until(check,seconds):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            value=check()
            if value:return value
            time.sleep(2)
        raise TimeoutError('Actual state remains pending')
    save()
    for index in sorted(retries):
        service='closure-observation-'+str(index).zfill(2);record={'service':service,'status':'WAITING_CONFIGURATION'};report['results'].append(record);save()
        try:
            def configuration():
                if not prepared.exists():return None
                return next((d for d in json.loads(prepared.read_text())['definitions'] if d['service']==service),None)
            definition=until(configuration,900);record['definition']=definition
            versions=api('/api/v1/admin/ops-agents/'+definition['agentId']+'/versions',token=token)
            if not any(v['version']==definition['version'] and v['definitionHash']==definition['definitionHash'] and v['lifecycle']=='PUBLISHED' for v in versions):raise ValueError('Exact published version changed')
            session=retries[index]['sessionId']
            record['originalAttempt']={key:retries[index].get(key) for key in ['status','runId','episode','error','draft']}
            record.update(status='EXECUTION_REQUESTED_RESPONSE_UNCONFIRMED',sessionId=session);save()
            instruction=f'请在同一任务内重试此前尚未确认的只读资源核验；旧尝试和不足证据保留，不能算作已验收。本次重新实际观察并等待业务确认，不创建另一个独立故障样本。请只读核对项目 {PROJECT} 中测试服务 {service}，限定本次实际观察开始至结束区间；核对前后版本、epoch和配置摘要一致、20次实际订单GET全部成功且错误数0。列出项目、环境、资源身份与真实区间；执行结束后等待业务验收确认。'
            response=api('/api/v1/user/chat/sessions/'+session+'/messages','POST',{'projectId':PROJECT,'query':instruction,'mode':'AGENT','engine':'GRAPH','agentDefinitionId':definition['agentId'],'agentVersion':definition['version'],'metadata':{'executionType':'WORKFLOW'}},token,timeout=360)
            run=record['runId']=response['metadata']['runId'];record['status']='EXECUTING';save()
            def ended():
                value=h['facts'](run)
                return value if value['status'] in ['SUCCEEDED','FAILED','CANCELED'] else None
            record['run']=until(ended,480)
            if record['run']['status']!='SUCCEEDED':raise RuntimeError('Actual workflow failed')
            proof_path=output.with_name(output.stem+'-'+service+'-proof.json');proof=truth.inspect(run,service,proof_path);record['truthEvidence']=str(proof_path)
            if proof['status']!='PASS' or proof['deployedJarSha256']!=jar:raise ValueError('Actual truth or deployed artifact differs')
            def assigned():
                rows=h['rows']("SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id WHERE t.project_id="+q(PROJECT)+" AND t.status='ASSIGNED' AND t.source_run_ref="+q(run))
                if len(rows)>1:raise ValueError('Ambiguous episode')
                return rows[0] if rows else None
            episode=record['episode']=until(assigned,480);address='/api/v1/user/ops/task-acceptance/'+episode['episodeId'];suffix='?projectId='+PROJECT
            observation=next(r for r in proof['receipts'] if 'beforeState' in json.loads(r['output']).get('normalizedContent',{}));content=json.loads(observation['output'])['normalizedContent']
            expected={pointer:content[key] for pointer,key in [('/projectId','projectId'),('/environment','environment'),('/resourceKey','resourceKey'),('/status','status'),('/observationStart','observationStart'),('/observationEnd','observationEnd'),('/configurationUnchanged','configurationUnchanged'),('/requestCount','requestCount'),('/errorCount','errorCount'),('/allRequestsSucceeded','allRequestsSucceeded'),('/requestMethod','requestMethod'),('/routeDefinition','routeDefinition')]}
            for phase in ['beforeState','afterState']:
                for field in ['version','epoch','configurationDigest']:expected['/'+phase+'/'+field]=content[phase][field]
            review=f'请核验原定资源观察目标：项目 {PROJECT}、test环境、资源 service://{service}/test；仅覆盖本次实际 observationStart 至 observationEnd 区间。核对观察status PASSED、前后版本 observation-initial、epoch0、配置摘要相同、configurationUnchanged true，20次真实订单GET全部HTTP200且错误数0。本次重试的真实回执中，requestMethod与routeDefinition来自实际GET /orders调度，allRequestsSucceeded必须按保留的20个HTTP状态重算为true；请用这三个已有标量字段覆盖方法、路径和全部HTTP200条件，并保留其他所有身份、区间、前后版本epoch摘要及数量条件。旧不足证据仍保留，不把运行状态当验收。保留全部具体资源和区间条件，不宣称区间外或其他资源没有改变，也不把运行成功直接视为任务验收。'
            record.update(status='DRAFT_REQUESTED',reviewerInstruction=review);save()
            draft=record['draft']=api(address+'/draft'+suffix,'POST',{'revision':episode['revision'],'instruction':review},token,timeout=720);save()
            if draft['status']!='READY':record['status']='DRAFT_NOT_READY';save();continue
            criteria=draft['request']['criteria']
            if not all(any(c['pointer']==pointer and c['operator']=='EQ' and c['expected']==value and c['resultId']==observation['resultId'] and c['outputHash']==observation['hash'] for c in criteria) for pointer,value in expected.items()):
                record['status']='DRAFT_REQUIRES_REVIEW';record['requiredBusinessFields']=expected;save();continue
            record['status']='CONFIRMATION_REQUESTED_RESPONSE_UNCONFIRMED';save()
            verified=record['verification']=api(address+suffix,'POST',draft['request'],token,timeout=120)
            record['after']=api(address+suffix,token=token)
            record['status']='PASS_ACTUAL_ACCEPTANCE' if verified['outcome']=='SUCCEEDED' and record['after']['outcome']=='SUCCEEDED' and all(c['verdict']=='PASSED' for c in verified['checks']) else 'BUSINESS_VERIFICATION_FAILED'
        except Exception as error:
            record['interruptedStage']=record['status'];record['status']='FAILED_OR_UNCONFIRMED';record['error']=type(error).__name__+': '+str(error)
        finally:
            record['recordedAt']=dt.datetime.now(dt.timezone.utc).isoformat();save();print(json.dumps({'service':service,'status':record['status'],'runId':record.get('runId')}),flush=True)
    report['status']='PASS' if all(r['status']=='PASS_ACTUAL_ACCEPTANCE' for r in report['results']) else 'INCOMPLETE';report['completedAt']=dt.datetime.now(dt.timezone.utc).isoformat();save()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepared',type=Path,required=True);parser.add_argument('--prior-acceptance',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--last',type=int,default=21);parser.add_argument('--retry-batch',type=Path,required=True)
    args=parser.parse_args();main(args.prepared,args.prior_acceptance,args.output,args.last,args.retry_batch)
