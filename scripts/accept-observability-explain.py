#!/usr/bin/env python3
"""Normal natural-language task acceptance after an independent real MySQL cross-check.

This does not assign Episodes, manufacture outcomes or publish Skills. A model drafts
conditions; the script checks their exact coverage and immutable receipt before submitting.
Already verified revisions are retained without submitting another acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def accept(evidence_path, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Retain previous evidence and choose a new output')
    evidence = json.loads(evidence_path.read_text())
    if evidence.get('status') != 'PASS':
        raise ValueError('Run inspect-observability-explain.py successfully first')
    run = evidence['run'][0]['runId']
    identity = evidence['actualOrder']
    receipt = evidence['receipts'][0]
    if hashlib.sha256(receipt['output'].encode()).hexdigest() != receipt['hash']:
        raise ValueError('Independent receipt hash mismatch')
    content = json.loads(receipt['output'])['normalizedContent']
    expected = {'/scopeMapping/logicalEnvironment': 'acceptance',
                '/scopeMapping/serviceId': identity['serviceId'], '/orderId': identity['orderId']}
    tables = content['plan']['query_block']['nested_loop']
    if {row['table']['table_name'] for row in tables} != {'o', 'c', 's'}:
        raise ValueError('This acceptance requires the independently verified three-table order plan')
    for index, row in enumerate(tables):
        for field in ('access_type', 'key'):
            expected[f'/plan/query_block/nested_loop/{index}/table/{field}'] = row['table'][field]
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    query = ("SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) "
             "FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id "
             "AND e.project_id=t.project_id WHERE t.project_id='ops-acceptance-a' "
             "AND t.status='ASSIGNED' AND t.source_run_ref=" + h['quoted'](run))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query + ';\n')
    result = {'status': 'NOT_PASSED', 'runId': run, 'independentEvidence': str(evidence_path),
              'expectedBusinessChecks': expected,
              'boundary': 'Query plan result only; read-only enforcement is checked independently.'}
    try:
        episodes = h['rows'](query)
        if len(episodes) != 1:
            result['status'] = 'PENDING_EPISODE'
            return
        episode = result['episode'] = episodes[0]
        path = '/api/v1/user/ops/task-acceptance/' + episode['episodeId']
        suffix = '?projectId=ops-acceptance-a'
        before = result['before'] = h['api'](path + suffix, token=h['token'])
        if before['outcome'] == 'SUCCEEDED':
            result['status'] = 'ALREADY_VERIFIED_REVISION'
            return
        if not any(r['result_id'] == receipt['resultId'] and r['output_hash'] == receipt['hash']
                   for r in before['receipts']):
            raise ValueError('Current receipt identity differs from independent evidence')
        plans = '，'.join(f"{r['table']['table_name']} 表为 {r['table']['access_type']} 访问并使用 {r['table']['key']} 索引"
                         for r in tables)
        instruction = (f"请核验本次查询计划诊断的业务结果：环境为 acceptance，服务为 {identity['serviceId']}，"
                       f"订单为 {identity['orderId']}；实际 MySQL EXPLAIN 返回 {plans}。"
                       "只按已有回执中的这些字段计算结果。只读权限另由 MCP 策略和独立日志核对，"
                       "不要求从查询计划字段推断是否发生修改，也不把本次读取当成修复或发布。")
        result['reviewerInstruction'] = instruction
        result['status'] = 'DRAFT_REQUESTED'
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        draft = result['draft'] = h['api'](path + '/draft' + suffix, 'POST',
            {'revision': episode['revision'], 'instruction': instruction}, h['token'], timeout=720)
        if draft.get('status') != 'READY':
            result['status'] = 'DRAFT_NOT_READY'
            return
        criteria = draft['request']['criteria']
        if not all(any(c['pointer'] == pointer and c['operator'] == 'EQ' and c['expected'] == value
                       and c['resultId'] == receipt['resultId'] and c['outputHash'] == receipt['hash']
                       for c in criteria) for pointer, value in expected.items()):
            result['status'] = 'DRAFT_REQUIRES_REVIEW'
            return
        result['status'] = 'VERIFICATION_REQUESTED_RESPONSE_UNCONFIRMED'
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        verification = result['verification'] = h['api'](path + suffix, 'POST', draft['request'], h['token'], timeout=120)
        result['after'] = h['api'](path + suffix, token=h['token'])
        if verification['outcome'] != 'SUCCEEDED' or not all(c['verdict'] == 'PASSED' for c in verification['checks']):
            result['status'] = 'BUSINESS_VERIFICATION_FAILED'
            return
        assert result['after']['outcome'] == 'SUCCEEDED'
        result['status'] = 'PASS_ACTUAL_ACCEPTANCE'
    except Exception as error:
        result['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'runId': run}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    accept(args.evidence, args.output)
    status = json.loads(args.output.read_text())['status']
    raise SystemExit(0 if status in ('PASS_ACTUAL_ACCEPTANCE', 'ALREADY_VERIFIED_REVISION') else 1)
