#!/usr/bin/env python3
"""Create manual mixed-method input and a read-only workflow, never successful task or evolution state."""
from pathlib import Path
import argparse,json,runpy,hashlib
ROOT=Path(__file__).resolve().parents[1]
PROJECT='ops-acceptance-a'
SKILL='synthetic-composite-version-and-sql'
AGENT='workflow-skill-composite-readonly-20261104'

def prepare(output):
 if output.exists():raise ValueError('Choose fresh evidence; do not replace prior versions')
 h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'));api,token=h['api'],h['token']
 fixture={'skillId':SKILL,'name':'目录版本和订单计划联合核对（维护输入）',
  'description':'人工维护输入，包含两个独立职责：目录服务版本状态核对与订单MySQL执行计划核对。不是自动沉淀的成功结论。',
  'content':'# 两类只读核验的混合入口\n\n这是本机维护验收的人工方法输入，不是自动沉淀结果，也不代表生产故障修复。\n\n'
   '## 目录版本与状态\n针对明确指定的 discovery-service，先找到该服务已授权的只读状态工具，再读取实际回执。'
   '核对count=1、serviceId与目标一致，报告version和state，保留回执。版本和状态来自同一次当前读取；缺证据时停止。'
   '状态DEGRADED可以被如实读取，不得将读取成功解释为已经修复或持续健康。\n\n'
   '## 订单执行计划\n针对明确指定的 ops-acc-a-service 与订单，在acceptance逻辑环境调用授权sql_explain。'
   '核对project/environment/service/order归属与实际查询模板，读取真实EXPLAIN FORMAT=JSON逐表的access_type、key、used_key_parts；'
   '只交付优化器计划及其适用对象，不把PRIMARY/const当作业务健康、根因或性能改善证明，不执行任意SQL或DDL/DML。\n\n'
   '## 职责与维护边界\n两套步骤相互独立，目录状态不能代替数据库计划，数据库计划不能代替目录状态。'
   '用户只问其中一类时，不应加载并要求完成另一类配方。当前共用入口，两个职责需要各自可单独检索、修改和回退的边界；'
   '是否更新、拆分或保持由已验收来源及现有自动维护策略决定，不以达到数量代替证据。'
   '所有结论仅限各自资源及本次读取；不授予工具权限，不绕过审批、沙箱或执行中心。\n',
  'status':'ENABLED','origin':'MANUAL','updateMode':'AUTO','autoUpdateEnabled':True,'autoMergeEnabled':True,
  'category':'OBSERVABILITY','subcategory':'directory-and-mysql-plan',
  'whenToUse':['只读核对目录服务的当前版本状态','只读核对明确订单的MySQL优化器执行计划'],
  'whenNotToUse':['修复、发布、生产写入或任意SQL','持续健康、根因或性能收益验收','缺少明确对象或当前授权'],
  'keywords':['discovery-service','当前版本','健康状态','sql_explain','订单','MySQL','执行计划','单独职责']}
 skills='/api/v1/admin/ops/skills/projects/'+PROJECT
 if any(s['skillId']==SKILL for s in api(skills,token=token)):raise ValueError('Existing method is preserved; inspect before reusing')
 created=api(skills,'POST',fixture,token)
 bindings=['ops-03-5b20a8ca','ops-04-real-observability-3a77099e']
 tools=api('/api/v1/admin/ops/projects/'+PROJECT+'/tools',token=token)
 if not all(any(t['mcpId']==m and t['status']=='ENABLED' for t in tools) for m in bindings):raise ValueError('Only existing enabled readonly bindings are allowed')
 definition={'agentId':AGENT,'name':'目录与订单计划联合核对','description':'本机两类只读事实联合核验，正常自然语言输入，执行后另行用户业务验收。',
  'scopeType':'PROJECT','projectId':PROJECT,'engine':'GRAPH','schemaVersion':1,'definitionKind':'SPECIALIZED_WORKFLOW',
  'workflowInvocationMode':'MANUAL_ONLY','workflowAutoSelectEnabled':False,'queryRewriteEnabled':False,'ragEnabled':False,
  'startNodeId':'start','instruction':'按用户明确的两个对象分别进行只读核对；只使用已授权工具，不修改任何资源。',
  'skills':[SKILL],
  'nodes':[{'nodeId':'start','type':'START','config':{'inputKeys':['query'],'maxRealToolCalls':4}},
   {'nodeId':'inspect','type':'AGENT','mode':'REACT','agent':'readonly-composite-observer','modelId':'ops-acceptance-terra',
    'mcpIds':bindings,'skills':[SKILL],'outputKey':'inspectionReport',
    'instruction':'按当前绑定的方法及用户自然语言目标，分别读取目录服务状态和订单sql_explain。使用实际当前UTC时间构建工具要求的近期窗口。'
     '每一项保留各自服务/订单/环境、回执与原始字段；不得从一项推断另一项，不执行任何写操作。以正常中文分项说明，完成后等待业务验收。',
    'config':{'inheritProjectCapabilities':False,'contextInputs':['query']}},
   {'nodeId':'end','type':'END','config':{'outputKeys':['inspectionReport']}}],
  'edges':[{'from':a,'to':b,'conditionType':'always','condition':'always'} for a,b in [('start','inspect'),('inspect','end')]]}
 versions=api('/api/v1/admin/ops-agents/'+AGENT+'/versions',token=token)
 if versions:raise ValueError('Existing workflow history is preserved; inspect instead of overwriting')
 report={'status':'MANUAL_INPUT_CREATED_NO_TASK_OUTCOME','projectId':PROJECT,'manualMethod':created,'input':fixture,'definitionInput':definition,
  'boundary':'Only manual method and workflow configuration. No sources, task outcomes, model proposals, reviews, approvals or automatic publication are seeded.'}
 output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 saved=api('/api/v1/admin/ops-agents/drafts','POST',definition,token)
 address='/api/v1/admin/ops-agents/'+AGENT+'/versions/'+str(saved['version'])
 saved=api(address+'/validate','POST',{},token)
 saved=api(address+'/publish','POST',{},token)
 assert saved['lifecycle']=='PUBLISHED'
 report.update(status='READY_MANUAL_INPUT_AND_PUBLISHED_READONLY_WORKFLOW',definition=saved,sourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
 output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'status':report['status'],'agentId':AGENT,'version':saved['version'],'manualSkill':SKILL}),flush=True)

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path)
 prepare(parser.parse_args().output)
