#!/usr/bin/env python3
"""Register the two agreed models via normal admin APIs; retain conflicting existing records."""
from pathlib import Path
import argparse
import json
import runpy

ROOT = Path(__file__).resolve().parents[1]


def main(output):
    config = runpy.run_path(str(ROOT / 'scripts/enable-acceptance-models.py'))
    env = config['read_env'](ROOT / 'deploy/.env.acceptance')
    if config['decoded'](env.get('ORBISOPS_AI_MODEL_CALLS_ENABLED', '')) != 'true':
        raise ValueError('Enable the authorized acceptance models first')
    helper = runpy.run_path(str(ROOT / 'scripts/seed-local-acceptance.py'))
    token = helper['login'](json.loads((ROOT / 'deploy/.acceptance-private/admin.json').read_text()))
    api = helper['request']
    expected = {'apiId': 'ops-acceptance-authorized-models', 'providerName': '已授权模型网关（本地验收）',
                'providerType': 'openai', 'baseUrl': config['decoded'](env['ORBISOPS_MODEL_BASE_URL']).rstrip('/'),
                'apiKey': '${env:ORBISOPS_MODEL_API_KEY:}', 'completionsPath': '/v1/chat/completions',
                'embeddingsPath': '/v1/embeddings', 'status': 1}
    existing = api('/api/v1/admin/ai-client-api/query-all', token=token)
    provider = next((v for v in existing if v['apiId'] == expected['apiId']), None)
    created = []
    if provider is None:
        assert api('/api/v1/admin/ai-client-api/create', 'POST', expected, token) is True
        created.append(expected['apiId'])
    elif any(provider.get(k) != expected[k] for k in ('baseUrl', 'completionsPath', 'status')):
        raise ValueError('An existing provider differs; it was retained')
    models = api('/api/v1/admin/ai-client-model/query-all', token=token)
    for role, description in [('luna', '小模型：常规对话与任务分段'), ('terra', '大模型：复杂任务与 Skill 编写')]:
        desired = {'modelId': 'ops-acceptance-' + role, 'apiId': expected['apiId'],
                   'modelName': 'gpt-5.6-' + role, 'modelType': 'openai', 'modelUsage': 'CHAT',
                   'description': description, 'status': 1}
        current = next((v for v in models if v['modelId'] == desired['modelId']), None)
        if current is None:
            if any(v['modelName'] == desired['modelName'] for v in models):
                raise ValueError('An existing model binding differs; it was retained')
            assert api('/api/v1/admin/ai-client-model/create', 'POST', desired, token) is True
            created.append(desired['modelId'])
        elif any(current.get(k) != desired[k] for k in ('apiId', 'modelName', 'status')):
            raise ValueError('An existing model binding differs; it was retained')
    path = '/api/v1/admin/model-default-policy?projectId=ops-acceptance-a'
    policy = api(path, token=token)
    if policy and policy.get('defaultChatModelId') not in ('', 'ops-acceptance-luna'):
        raise ValueError('An existing default model differs; it was retained')
    if policy.get('defaultChatModelId') != 'ops-acceptance-luna':
        policy = api(path, 'PUT', {**policy, 'defaultChatModelId': 'ops-acceptance-luna', 'status': 'ENABLED'}, token)
        created.append('project-default-policy')
    result = {'status': 'PASS', 'created': created, 'defaultModelId': policy['defaultChatModelId'],
              'models': ['gpt-5.6-luna', 'gpt-5.6-terra'], 'credentialsStoredAsEnvironmentReference': True}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    main(parser.parse_args().output)
