#!/usr/bin/env python3
"""Seed two explicitly synthetic overlapping method inputs, without seeding an evolution decision.

Only missing project Skills are created through the ordinary management API. Prior versions,
user changes, auto-evolution, retirement and rollback are retained on repeated invocations.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def fixture(letter):
    return {'skillId': 'synthetic-discovery-status-duplicate-' + letter,
        'name': '目录服务版本与健康核对 · 合成重复方法 ' + letter.upper(),
        'description': '对本地 discovery-service 目录验收服务查询当前版本与健康状态，保留真实工具依据。',
        'content': '# 目录服务版本与健康核对\n\n此文件是本地合成方法维护的输入，不代表自动沉淀结果。\n\n'
            '适用于隔离项目中的 discovery-service 目录服务只读现状核对。先确认用户要求的具体服务；'
            '按当前授权目录搜索该服务的只读状态工具，加载真实参数定义后调用。'
            '从实际工具回执读取服务标识、版本和健康状态，并引用本次请求与证据。'
            'HEALTHY 与 DEGRADED 都是查询到的现状，不能把查询完成写成已经修复。'
            '工具报错、无证据或对象不匹配时停止结论并说明缺口。'
            '本方法不授予工具权限，不执行写入，不替代审批、发布或持续业务观测。\n',
        'status': 'ENABLED', 'origin': 'MANUAL', 'updateMode': 'AUTO',
        'autoUpdateEnabled': True, 'autoMergeEnabled': True,
        'category': 'OBSERVABILITY', 'subcategory': 'discovery-service-status',
        'whenToUse': ['用户要求只读核对 discovery-service 目录验收服务的版本与健康状态'],
        'whenNotToUse': ['用户要求实际修复或发布', '需要持续窗口证明业务恢复', '服务不在当前授权目录中'],
        'keywords': ['discovery-service', '版本', '健康状态', '只读核对']}


def seed(output):
    if output.exists():
        raise ValueError('Choose a new evidence filename')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    path = '/api/v1/admin/ops/skills/projects/ops-acceptance-a'
    actions = []
    for letter in ('a', 'b'):
        desired = fixture(letter)
        existing = next((s for s in h['api'](path, token=h['token']) if s['skillId'] == desired['skillId']), None)
        if existing is None:
            existing = h['api'](path, 'POST', desired, h['token'])
            action = 'CREATED_SYNTHETIC_INPUT'
        else:
            versions = h['api'](path + '/' + desired['skillId'] + '/versions', token=h['token'])
            initial = next((v for v in versions if v.get('version') == 1), None)
            if initial is None or initial.get('content') != desired['content']:
                raise ValueError('Conflicting existing Skill retained without modification')
            action = 'PRESERVED_EXISTING'
        actions.append({'skillId': desired['skillId'], 'action': action, 'version': existing['currentVersion'],
            'originalContentHash': hashlib.sha256(desired['content'].encode()).hexdigest()})
    result = {'status': 'SEEDED_SYNTHETIC_INPUT_ONLY', 'actions': actions,
        'boundary': 'No sources, acceptance outcomes, model proposals, merges or splits have been manufactured.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    seed(args.output)
