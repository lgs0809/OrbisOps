#!/usr/bin/env python3
"""Create an isolated, synthetic long-method fixture through normal APIs. Never seeds review/publication outcomes."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-maintenance-acceptance'
SKILL = 'synthetic-adjacent-prose-maintenance'


def source():
    paragraphs = []
    for dimension in ('service identity', 'environment identity', 'observation start', 'observation end',
                      'sampling interval', 'sampling gaps', 'counter resets', 'request population',
                      'error classification', 'histogram boundaries', 'percentile interpretation',
                      'sample sufficiency', 'data freshness', 'collection health', 'resource identity',
                      'version identity', 'scope restrictions', 'permission boundaries', 'missing evidence',
                      'conflicting evidence', 'provider provenance', 'response integrity', 'result limitations',
                      'stop conditions'):
        paragraph = (f'The {dimension} check belongs only to this synthetic maintenance fixture. '
            'Read the already supplied evidence and preserve its original scope, timestamp, identity and source reference. '
            'If the relevant fact is unavailable or contradictory, record the limitation and stop that conclusion. '
            'Do not infer a successful operational outcome from a completed model response or an accessible page. '
            'This fixture grants no tool capability and no production permission. Actual operations require their existing authorized workflow.')
        paragraphs.extend([paragraph, paragraph])
    content = '# Synthetic maintenance regression\n\nRead resources/boundary.json before use.\n\n' + '\n\n'.join(paragraphs)
    return {'skillId': SKILL, 'name': '合成维护检查夹具', 'description': '仅用于相邻重复正文精简验收，不提供真实运维结论。',
            'content': content, 'status': 'ENABLED', 'origin': 'MANUAL', 'updateMode': 'AUTO',
            'autoUpdateEnabled': True, 'autoMergeEnabled': False, 'category': 'GENERAL', 'subcategory': 'synthetic-maintenance',
            'whenToUse': ['仅用于明确标记的本机合成方法维护验收'], 'whenNotToUse': ['真实生产操作', '判断实际服务是否健康'],
            'keywords': ['synthetic-maintenance'], 'artifacts': [{'path': 'resources/boundary.json', 'role': 'RESOURCE',
                'content': json.dumps({'fixture': True, 'productionPermission': False, 'resultBoundary': 'Not business acceptance'}, sort_keys=True)}]}


def semantic_source():
    desired = source()
    desired['skillId'] = 'synthetic-semantic-method-maintenance'
    desired['name'] = '合成语义整理验收'
    desired['content'] = '# Synthetic semantic maintenance\n\nRead resources/method.json and resources/boundary.json before use.\n'
    rules = []
    for dimension in ('service identity', 'environment scope', 'observation window', 'sampling gaps',
                      'counter resets', 'sample sufficiency', 'provider provenance', 'stop conditions'):
        for phrasing in (
                'Check {dimension} against the supplied evidence before reaching any conclusion.',
                'A conclusion requires checking the supplied evidence for {dimension}.',
                'Validate {dimension} from the evidence already provided, before deciding the result.',
                'Use the provided evidence to establish {dimension}; do this before concluding.',
                'Before any judgment, confirm {dimension} using the evidence in this fixture.',
                'The evidence must establish {dimension} first; only then consider a conclusion.'):
            rules.append(phrasing.format(dimension=dimension) +
                ' If the fact is absent or contradictory, record that limitation and stop the affected conclusion.' +
                ' This synthetic method grants no tools or production permission and does not demonstrate actual service health.' +
                ' Do not invent missing facts, replace real observations with model opinions, or treat a completed response as business success.')
    method = {'fixture': True, 'rules': rules,
              'acceptance': 'Report only what supplied evidence supports, preserving its original scope and limitations.',
              'stop': ['Stop a conclusion when its required fact is absent or contradictory.',
                       'Any actual operation requires its existing authorized workflow.']}
    desired['artifacts'].append({'path': 'resources/method.json', 'role': 'RESOURCE',
                                'content': json.dumps(method, ensure_ascii=False, indent=2)})
    return desired


def main(output, semantic=False):
    if output.exists(): raise ValueError('Retain earlier evidence; choose a new output')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    api, token = support['api'], support['token']
    projects = api('/api/v1/admin/ops-projects/snapshot', token=token)['projects']
    if not any(p['projectId'] == PROJECT for p in projects):
        api('/api/v1/admin/ops-projects/projects', 'POST', {'projectId': PROJECT, 'name': '合成方法维护验收',
            'owner': 'ops_acceptance_admin', 'description': 'synthetic-maintenance-only; no business success seeded',
            'environments': ['test']}, token)
    path = '/api/v1/admin/ops/skills/projects/' + PROJECT
    desired = semantic_source() if semantic else source()
    skill_id = desired['skillId']
    catalog = api(path, token=token)
    existing = next((item for item in catalog if item.get('skillId') == skill_id), None)
    if existing is None:
        existing = api(path, 'POST', desired, token)
    else:
        versions = api(path + '/' + skill_id + '/versions', token=token)
        initial = next((item for item in versions if item.get('version') == 1), None)
        expected_hashes = {a['path']: hashlib.sha256(a['content'].encode()).hexdigest() for a in desired['artifacts']}
        if initial is None or initial.get('content') != desired['content'] or any(
                initial.get('artifactHashes', {}).get(path) != digest for path, digest in expected_hashes.items()):
            raise ValueError('Existing fixture differs; retained without overwrite')
    proof = {'status': 'SEEDED_SYNTHETIC_INPUT_ONLY', 'projectId': PROJECT, 'skillId': skill_id,
        'version': existing.get('currentVersion'), 'skillHash': existing.get('currentSkillHash'),
        'originalContentHash': hashlib.sha256(desired['content'].encode()).hexdigest(),
        'originalContentBytes': len(desired['content'].encode()), 'originalContentChars': len(desired['content']),
        'originalArtifactHashes': {a['path']: hashlib.sha256(a['content'].encode()).hexdigest() for a in desired['artifacts']},
        'boundary': 'API-created manual fixture. Automatic review, new version, index and rollback must execute normally.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(proof, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--semantic', action='store_true', help='Short entry and long referenced method with semantic redundancy')
    args = parser.parse_args()
    main(args.output, args.semantic)
