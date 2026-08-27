#!/usr/bin/env python3
"""Freeze read-first structural review workflows; never consult reference labels."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[1]


def prepare(baseline_path, corpus, output):
    spec = importlib.util.spec_from_file_location('skill_eval_common', ROOT/'scripts/run-platform-inspection-eval.py')
    common = importlib.util.module_from_spec(spec); spec.loader.exec_module(common)
    corpus = corpus.resolve()
    common.DATASET, common.CORPUS = 'skill', corpus
    manifest = common.verify_corpus()
    baseline = json.loads(baseline_path.read_text())
    if baseline['manifest'] != manifest: raise RuntimeError('Frozen baseline changed')
    integrations = {}
    cases = json.loads((corpus/'facts.json').read_text())
    for project in {case['projectId'] for case in cases}:
        name = 'OPS platform frozen skill '+project.rsplit('-', 1)[-1]
        if manifest.get('configurationRevision'): name += ' '+corpus.name.rsplit('-', 1)[-1]
        matches = [m for m in common.configuration_api('/api/v1/admin/ops/projects/'+project+'/tools', token=common.TOKEN) if m['mcpName'] == name]
        if len(matches) != 1 or matches[0]['status'] != 'ENABLED': raise RuntimeError('Exact reviewed read-only integration required')
        integrations[project] = matches[0]['mcpId']
    report = {'scope': manifest['scope'], 'manifest': manifest, 'definitions': {}, 'configurations': {},
        'requiresReceiptBeforeModel': True, 'group': 'NATIVE_STRUCTURAL_TRUTH_THEN_PHYSICAL_READ_THEN_TERRA',
        'baselinePreparedSha256': hashlib.sha256(baseline_path.read_bytes()).hexdigest(),
        'configurationBuilderSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'referenceLabelsConsultedForConfiguration': False, 'peerIsolation': common.mount_isolation()}
    for case in cases:
        time.sleep(0.7)
        value = common.definition(case['projectId'], integrations[case['projectId']])
        value['agentId'] = 'ops-platform-skill-read-first-'+case['caseId']+'-v1'
        node = copy.deepcopy(value['nodes'][1]); node.update(nodeId='review', mode='LLM', mcpIds=[])
        node['instruction'] = node['instruction'].replace('先加载skill_snapshot完整Schema并读取用户指定场景。',
            '前置节点已通过实际只读skill_snapshot取得本场景。读取可见上下文workflowData_snapshot中的完整normalizedContent，先核对场景和项目身份。')
        node['config']['contextInputs'] = ['query', 'workflowData_snapshot']
        reader = {'nodeId': 'read-snapshot', 'type': 'AGENT', 'mode': 'DIRECT', 'agent': 'ops-platform-eval-reader',
            'mcpIds': [integrations[case['projectId']]], 'outputKey': 'observationReceipt', 'config': {
                'inheritProjectCapabilities': False, 'actions': [{'mcpId': integrations[case['projectId']],
                    'remoteToolName': 'skill_snapshot', 'arguments': {'caseId': case['caseId'], 'projectId': case['projectId']},
                    'structuredOutputKey': 'snapshot', 'outputMode': 'MCP_EVIDENCE_REFERENCE'}]}}
        value['nodes'] = [value['nodes'][0], reader, node, value['nodes'][-1]]
        value['edges'] = [{'from': a, 'to': b, 'conditionType': 'always', 'condition': 'always'}
            for a, b in [('start', 'read-snapshot'), ('read-snapshot', 'review'), ('review', 'end')]]
        versions = common.configuration_api('/api/v1/admin/ops-agents/'+value['agentId']+'/versions', token=common.TOKEN)
        if versions:
            saved = versions[0]
            if common.normalized_graph_parts(saved) != common.normalized_graph_parts(value):
                raise RuntimeError('Existing read-first configuration differs; retained')
        else:
            saved = common.configuration_api('/api/v1/admin/ops-agents/drafts', 'POST', value, common.TOKEN)
        path = '/api/v1/admin/ops-agents/'+saved['agentId']+'/versions/'+str(saved['version'])
        if saved['lifecycle'] == 'DRAFT': saved = common.configuration_api(path+'/validate', 'POST', {}, common.TOKEN)
        if saved['lifecycle'] == 'VALIDATED': saved = common.configuration_api(path+'/publish', 'POST', {}, common.TOKEN)
        if saved['lifecycle'] != 'PUBLISHED': raise RuntimeError('Configuration is not published')
        report['definitions'][case['caseId']] = {**{key: saved[key] for key in ['agentId', 'version', 'definitionHash']},
            **{key: baseline['definitions'][case['projectId']][key] for key in ['evaluatorUserId', 'evaluatorUsername']}}
        report['configurations'][case['caseId']] = value
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        print(json.dumps({'caseId': case['caseId'], 'stage': 'PUBLISHED_CONFIGURATION_ONLY'}), flush=True)
    report['status'] = 'READY'; output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists(): parser.error('Retain previous evidence; choose a new output filename')
    prepare(args.baseline, args.corpus, args.output)
