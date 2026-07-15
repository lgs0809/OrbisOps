#!/usr/bin/env python3
"""Exercise natural-language draft -> normal acceptance API on a real completed discovery task.

Uses the previously retained independent peer/SQLite check. It does not assign Episodes,
change SQL outcomes, publish Skills or treat transport success as business acceptance.
"""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def accept(evidence_path, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new output file and preserve previous results')
    evidence = json.loads(evidence_path.read_text())
    if evidence.get('status') != 'PASS':
        raise ValueError('An independent successful peer/SQLite cross-check is required first')
    run = evidence['run'][0]['runId']
    service = evidence['actualService']
    number = int(service['service_id'].removeprefix('discovery-service-'))
    expected = {'service_id': f'discovery-service-{number:02d}', 'version': f'discovery-v{number}',
                'state': 'DEGRADED' if number % 7 == 0 else 'HEALTHY'}
    if service != expected:
        raise ValueError('Actual service state differs from the repeatable fixture; inspect it before accepting')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    query = "SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id WHERE t.project_id='ops-acceptance-a' AND t.status='ASSIGNED' AND t.source_run_ref=" + h['quoted'](run)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query + ';\n')
    result = {'status': 'NOT_PASSED', 'runId': run, 'independentEvidence': str(evidence_path), 'expectedService': expected}
    try:
        episodes = h['rows'](query)
        if len(episodes) != 1:
            result['status'] = 'PENDING_EPISODE'
            return
        episode = episodes[0]
        result['episode'] = episode
        path = '/api/v1/user/ops/task-acceptance/' + episode['episodeId']
        suffix = '?projectId=ops-acceptance-a'
        result['before'] = h['api'](path + suffix, token=h['token'])
        instruction = f"核验本次只读查询任务：真实查询对象是 {expected['service_id']}，实际版本为 {expected['version']}，健康状态为 {expected['state']}。请据保留的工具回执逐项验证服务标识、版本和健康状态；不把这次读取当成修复或变更。"
        result['reviewerInstruction'] = instruction
        draft = h['api'](path + '/draft' + suffix, 'POST', {'revision': episode['revision'], 'instruction': instruction}, h['token'], timeout=720)
        result['draft'] = draft
        if draft.get('status') != 'READY':
            result['status'] = 'DRAFT_NOT_READY'
            return
        criteria = draft['request']['criteria']
        expected_checks = {'/serviceId': expected['service_id'], '/version': expected['version'], '/state': expected['state']}
        if not all(any(c['pointer'] == pointer and c['operator'] == 'EQ' and c['expected'] == value for c in criteria)
                   for pointer, value in expected_checks.items()):
            result['status'] = 'DRAFT_REQUIRES_REVIEW'
            return
        result['verification'] = h['api'](path + suffix, 'POST', draft['request'], h['token'], timeout=120)
        result['after'] = h['api'](path + suffix, token=h['token'])
        outcome = result['verification']
        assert outcome['outcome'] == 'SUCCEEDED' and all(c['verdict'] == 'PASSED' for c in outcome['checks'])
        assert result['after']['outcome'] == 'SUCCEEDED'
        result['status'] = 'PASS_ACTUAL_ACCEPTANCE'
    except Exception as error:
        result['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'runId': run, 'serviceId': expected['service_id']}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    accept(args.evidence, args.output)
