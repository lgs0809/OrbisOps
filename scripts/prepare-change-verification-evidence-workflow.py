#!/usr/bin/env python3
"""Add actual read receipts to the existing change review graph without changing its SLO.

Existing conversations remain pinned. The next published version only adds a
full ledger read and a deployment-bound native read when that exact package is
selected; other packages retain their existing review behavior. No model can
replace the native reader's project, package, URL, principal or approved tuple.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def definition(old, binding):
    value = copy.deepcopy(old)
    for field in ('version', 'definitionHash', 'lifecycle', 'validationStatus', 'publishedAt',
                  'publishedBy', 'createTime', 'updateTime'):
        value.pop(field, None)
    mcp = next(node for node in old['nodes'] if node['nodeId'] == 'version')['mcpIds'][0]
    router = {'nodeId':'native-evidence-applicability', 'type':'ROUTER', 'agent':'ops-observability-rules',
              'description':'只在已选中固定批准包时补充既有viewer原生只读证据',
              'config':{'routeMode':'single', 'inputKey':'workflowData_context', 'inputFormat':'JSON'}}
    native = {'nodeId':'native-approved-receipts', 'type':'AGENT', 'mode':'DIRECT',
              'agent':'ops-observability-reader', 'mcpIds':[mcp],
              'description':'既有viewer四GET；固定包身份；保留原批准与执行回执',
              'config':{'inheritProjectCapabilities':False, 'actions':[{
                  'mcpId':mcp, 'remoteToolName':'change_package_evidence',
                  'argumentBindings':{'window':'nodeOutput.workflowData_context.afterWindow.queryScope'},
                  'structuredOutputKey':'nativeApprovedEvidence', 'outputMode':'MCP_EVIDENCE_REFERENCE'}]}}
    def ledger(node_id):
        return {'nodeId':node_id, 'type':'AGENT', 'mode':'DIRECT', 'agent':'ops-observability-reader',
                'mcpIds':[mcp], 'description':'完整真实HTTP账本两窗计数/QPS/p95/相对变化；不派生SLO成功',
                'config':{'inheritProjectCapabilities':False, 'actions':[{
                    'mcpId':mcp, 'remoteToolName':'sql_window',
                    'argumentBindings':{
                        'window':'nodeOutput.workflowData_context.afterWindow.queryScope',
                        'comparisonWindow':'nodeOutput.workflowData_context.beforeWindow.queryScope'},
                    'structuredOutputKey':'completeRequestLedger', 'outputMode':'MCP_EVIDENCE_REFERENCE'}]}}
    value['nodes'].extend([router, native, ledger('complete-request-ledger'), ledger('complete-request-ledger-extended')])
    for edge in value['edges']:
        if edge['from']=='context' and edge['to']=='version':
            edge['to']='native-evidence-applicability'
        elif edge['from']=='extend' and edge['to']=='version-extended':
            edge['to']='complete-request-ledger-extended'
    def edge(source,target,kind='always',condition='always'):
        return {'from':source,'to':target,'conditionType':kind,'condition':condition,'dataMapping':{}}
    ref = 'nodeOutput.changeRef.'
    applies = ('nodeOutput.projectId == '+json.dumps(binding['projectId'])+' && '
               +ref+'packageId == '+json.dumps(binding['packageId'])+' && '
               +ref+'approvedVersion == '+str(binding['approvedVersion'])+' && '
               +ref+'approvedPackageHash == '+json.dumps(binding['approvedPackageHash']))
    value['edges'].extend([
        edge('native-evidence-applicability','native-approved-receipts','expression',applies),
        edge('native-evidence-applicability','complete-request-ledger','default','default'),
        edge('native-approved-receipts','complete-request-ledger'),
        edge('complete-request-ledger','version'),
        edge('complete-request-ledger-extended','version-extended')])
    return value


def prepare(previous,binding_file,output,publish):
    if output.exists(): raise ValueError('Retain previous workflow evidence')
    old=json.loads(previous.read_text());binding=json.loads(binding_file.read_text())['binding']
    value=definition(old,binding)
    report={'status':'PREPARED_ONLY','priorPinnedDefinition':str(previous),
            'priorPinnedDefinitionHash':old['definitionHash'], 'nativeReadBinding':binding,
            'originalSloPolicyChanged':False, 'existingSessionBindingChanged':False,
            'builderSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'definition':value}
    if publish:
        h=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
        saved=h['api']('/api/v1/admin/ops-agents/drafts','POST',value,h['token'])
        report['draft']=saved;output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        path='/api/v1/admin/ops-agents/'+saved['agentId']+'/versions/'+str(saved['version'])
        saved=h['api'](path+'/validate','POST',{},h['token'])
        report['validated']=saved;output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        saved=h['api'](path+'/publish','POST',{},h['token'])
        report['published']=saved;report['status']='PUBLISHED' if saved['lifecycle']=='PUBLISHED' else 'FAIL'
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'originalSloPolicyChanged':False,
                      'publishedVersion':report.get('published',{}).get('version')}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--previous',type=Path,required=True)
    p.add_argument('--binding',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--publish',action='store_true');a=p.parse_args()
    prepare(a.previous,a.binding,a.output,a.publish)
