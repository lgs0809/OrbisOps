#!/usr/bin/env python3
"""Publish the comparison group using only frozen inputs, never reference labels.

Both groups use the same inspection_snapshot, project, exact Terra model and
permissions. Workflow reads once, applies declared evidence/threshold routes and
uses one reporting Agent. This freezes configuration, not evaluation results.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[1]
common = None


def without_nulls(value):
    if isinstance(value, dict): return {key: without_nulls(child) for key, child in value.items() if child is not None}
    if isinstance(value, list): return [without_nulls(child) for child in value]
    return value


def route_rules(case):
    """Bounded JSON Rule AST using public requested target identity and observations."""
    target = case['facts']['target']
    p = 'nodeOutput.normalizedContent.observation'
    def compare(op, field, value): return {'op': op, 'field': p+'.'+field, 'value': value}
    def all_of(*rules): return {'op': 'ALL', 'rules': list(rules)}
    def any_of(*rules): return {'op': 'ANY', 'rules': list(rules)}
    matching = all_of(*[compare('EQ', 'scope.'+key, target[key]) for key in
        ['projectId', 'environment', 'serviceId', 'endTime', 'windowMinutes']])
    sufficient = all_of(matching, compare('EQ', 'collectionComplete', True), compare('GTE', 'requestCount', 100),
        compare('NE', 'p95Ms', None), compare('NE', 'errorRate', None))
    abnormal = any_of(all_of(matching, compare('EQ', 'reachable', False)),
        all_of(sufficient, any_of(compare('GT', 'errorRate', 0.01), compare('GT', 'p95Ms', 1000))))
    healthy = all_of(sufficient, compare('EQ', 'reachable', True),
        compare('LTE', 'errorRate', 0.01), compare('LTE', 'p95Ms', 1000))
    return {'abnormal': abnormal, 'healthy': healthy}


def definition(case, mcp):
    value = common.definition(case['projectId'], mcp)
    value.update(agentId='ops-platform-workflow-'+case['caseId']+'-'+common.CORPUS.name.rsplit('-', 1)[-1]+'-cfg2',
        name='冻结巡检Workflow · '+case['caseId'],
        instruction='只读获取同一冻结观测。固定规则区分对象/窗口、证据适用性和业务异常，再按真实观测生成报告。')
    reader = {'nodeId': 'read-snapshot', 'type': 'AGENT', 'mode': 'DIRECT', 'agent': 'ops-platform-eval-reader',
        'mcpIds': [mcp], 'outputKey': 'observationReceipt', 'config': {'inheritProjectCapabilities': False,
            'actions': [{'mcpId': mcp, 'remoteToolName': 'inspection_snapshot',
                'arguments': {'caseId': case['caseId'], 'projectId': case['projectId']},
                'structuredOutputKey': 'snapshot', 'outputMode': 'MCP_EVIDENCE_REFERENCE'}]}}
    route = {'nodeId': 'classify', 'type': 'ROUTER', 'agent': 'ops-platform-eval-rules',
        'config': {'inputKey': 'workflowData_snapshot', 'inputFormat': 'JSON', 'routeMode': 'single'}}
    reporters = []
    for key, label in [('healthy', '健康'), ('abnormal', '异常'), ('insufficient', '证据不足')]:
        node = copy.deepcopy(value['nodes'][1])
        node.update(nodeId='report-'+key, mode='LLM', agent='ops-platform-eval-report', mcpIds=[], outputKey='inspection')
        node['instruction'] = ('本节点是固定规则路由后的报告节点，当前分支为'+label+'。'
            '读取可见上下文 workflowData_snapshot 中的完整 normalizedContent，按实际项目、对象与时间窗解释结论。'
            '只能引用本次实际回执中的观测，不能读取参考答案，不能调用其他工具或写记忆/Skill/Task验收。'
            '如回执与分支矛盾，如实输出证据不足；不编造数据。输出与基线相同的内部JSON：conclusion、caseId、reason，'
            'conclusion为健康、异常或证据不足，reason自然中文解释。用户不填写JSON。')
        node['config']['contextInputs'] = ['query', 'workflowData_snapshot']
        reporters.append(node)
    value['nodes'] = [value['nodes'][0], reader, route, *reporters, value['nodes'][-1]]
    # A fixed case ID is an input binding. All cases receive exactly the same
    # rules; no expected label, reference or family information is consulted.
    rules = route_rules(case)
    value['edges'] = [{'from': 'start', 'to': 'read-snapshot', 'conditionType': 'always', 'condition': 'always'},
        {'from': 'read-snapshot', 'to': 'classify', 'conditionType': 'always', 'condition': 'always'},
        {'from': 'classify', 'to': 'report-abnormal', 'conditionType': 'expression', 'condition': common.canonical(rules['abnormal'])},
        {'from': 'classify', 'to': 'report-healthy', 'conditionType': 'expression', 'condition': common.canonical(rules['healthy'])},
        {'from': 'classify', 'to': 'report-insufficient', 'conditionType': 'default', 'condition': 'default'},
        *[{'from': n['nodeId'], 'to': 'end', 'conditionType': 'always', 'condition': 'always'} for n in reporters]]
    # Freeze the same documented defaults that the server persists, so a
    # failed publication can resume only when the entire graph still matches.
    route['outputKey'] = 'selectedRoutes'
    for node in value['nodes']:
        for key in ['mcpServers', 'mcpIds', 'executionTargetIds', 'skills']: node.setdefault(key, [])
        if node.get('type') == 'AGENT':
            node['config'].update(mode=node['mode'].lower(), role='general')
    for edge in value['edges']: edge['dataMapping'] = {}
    return value


def prepare(react_prepared, output, corpus):
    global common
    spec = importlib.util.spec_from_file_location('inspection_common', ROOT/'scripts/run-platform-inspection-eval.py')
    common = importlib.util.module_from_spec(spec); spec.loader.exec_module(common)
    common.API = common.configuration_api
    corpus = corpus.resolve()
    common.CORPUS = corpus
    manifest = common.verify_corpus()
    if manifest.get('configurationRevision') != 'v2-derived-rate':
        raise RuntimeError('Reviewed comparison revision with derived errorRate required')
    baseline = json.loads(react_prepared.read_text())
    if baseline['manifest'] != manifest: raise RuntimeError('Baseline facts/reference changed')
    cases = json.loads((common.CORPUS/'facts.json').read_text())
    integrations = {}
    for project in {c['projectId'] for c in cases}:
        matches = [m for m in common.API('/api/v1/admin/ops/projects/'+project+'/tools', token=common.TOKEN)
            if m['mcpName'] == 'OPS platform frozen inspection '+project.rsplit('-', 1)[-1]+' '+corpus.name.rsplit('-', 1)[-1]]
        if len(matches) != 1 or matches[0]['status'] != 'ENABLED': raise RuntimeError('Baseline tool is not unchanged/enabled')
        integrations[project] = matches[0]['mcpId']
    report = {'scope': manifest['scope'], 'group': 'WORKFLOW_DECLARED_RULES_PLUS_TERRA_REPORT',
        'manifest': manifest, 'definitions': {}, 'configurations': {},
        'baselinePreparedSha256': hashlib.sha256(react_prepared.read_bytes()).hexdigest(),
        'configurationBuilderSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'referenceLabelsConsultedForConfiguration': False}
    for case in cases:
        time.sleep(0.7)
        config = definition(case, integrations[case['projectId']])
        versions = common.API('/api/v1/admin/ops-agents/'+config['agentId']+'/versions', token=common.TOKEN)
        if versions:
            saved = versions[0]
            if without_nulls(saved.get('nodes')) != without_nulls(config['nodes']) or without_nulls(saved.get('edges')) != without_nulls(config['edges']):
                raise RuntimeError('Existing comparison differs from frozen configuration; retained')
        else:
            saved = common.API('/api/v1/admin/ops-agents/drafts', 'POST', config, common.TOKEN)
        path = '/api/v1/admin/ops-agents/'+saved['agentId']+'/versions/'+str(saved['version'])
        if saved['lifecycle'] == 'DRAFT': saved = common.API(path+'/validate', 'POST', {}, common.TOKEN)
        if saved['lifecycle'] == 'VALIDATED': saved = common.API(path+'/publish', 'POST', {}, common.TOKEN)
        if saved['lifecycle'] != 'PUBLISHED': raise RuntimeError('Configuration is not published')
        report['definitions'][case['caseId']] = {**{k: saved[k] for k in ['agentId', 'version', 'definitionHash']},
            **{k: baseline['definitions'][case['projectId']][k] for k in ['evaluatorUserId', 'evaluatorUsername']}}
        report['configurations'][case['caseId']] = config
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        print(json.dumps({'caseId': case['caseId'], 'definitionHash': saved['definitionHash'], 'stage': 'PUBLISHED_CONFIGURATION_ONLY'}), flush=True)
    report['status'] = 'READY'; output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--react-prepared', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists(): parser.error('Retain previous evidence; choose a new output filename')
    prepare(args.react_prepared, args.output, args.corpus)
