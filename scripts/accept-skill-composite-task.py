#!/usr/bin/env python3
"""Natural-language criteria draft and normal acceptance for independently checked dual facts."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'


def plan_fields(value, path=''):
    fields = {}
    if isinstance(value, dict):
        if 'table_name' in value:
            for key in ('table_name', 'access_type', 'key'):
                if isinstance(value.get(key), str):
                    fields[path + '/' + key] = value[key]
            for index, part in enumerate(value.get('used_key_parts', [])):
                if isinstance(part, str):
                    fields[path + '/used_key_parts/' + str(index)] = part
        for key, child in value.items():
            fields.update(plan_fields(child, path + '/' + key.replace('~', '~0').replace('/', '~1')))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            fields.update(plan_fields(child, path + '/' + str(index)))
    return fields


def plan_descriptions(value):
    """Describe actual optimizer fields as prose; users never need to type JSON pointers."""
    descriptions = []
    if isinstance(value, dict):
        if isinstance(value.get('table_name'), str):
            descriptions.append('表 ' + value['table_name'] + ' 的访问方式为 ' + str(value.get('access_type'))
                + '，索引为 ' + str(value.get('key')) + '，使用的索引列为 '
                + '、'.join(str(part) for part in value.get('used_key_parts', [])))
        for child in value.values():
            descriptions.extend(plan_descriptions(child))
    elif isinstance(value, list):
        for child in value:
            descriptions.extend(plan_descriptions(child))
    return descriptions


def accept(evidence_path, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Preserve earlier outcomes')
    evidence = json.loads(evidence_path.read_text())
    if evidence.get('status') != 'PASS_TWO_INDEPENDENT_ACTUAL_RESOURCE_CHAINS':
        raise ValueError('First independently verify both actual resources')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    run = evidence['runId']
    query = "SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id WHERE t.project_id='ops-acceptance-a' AND t.status='ASSIGNED' AND t.source_run_ref=" + h['quoted'](run)
    output.with_suffix('.sql').write_text(query + ';\n')
    native = evidence['observations']
    directory = native['directory']['observed']
    sql = native['sql']['observed']
    expected = {
        'directory': {('/' + key): directory[key] for key in ('count', 'serviceId', 'version', 'state')},
        'sql': dict({'/orderId': sql['orderId']}, **plan_fields(sql['plan'], '/plan')),
    }

    def valid_checks(checks, saved=False):
        for kind, fields in expected.items():
            identities = {native[kind][key]['resultId']: native[kind][key]['hash'] for key in ('receipt', 'projection')}
            for pointer, value in fields.items():
                if not any(c.get('pointer') == pointer and c.get('operator') == 'EQ'
                           and c.get('expected') == value and c.get('resultId') in identities
                           and c.get('outputHash') == identities[c['resultId']]
                           and (not saved or c.get('actual') == value and c.get('verdict') == 'PASSED') for c in checks):
                    return False
        return True

    report = {'status': 'NOT_PASSED', 'runId': run, 'pair': evidence['pair'],
              'independentEvidence': str(evidence_path), 'requiredNativeCriteria': expected}
    try:
        episodes = h['rows'](query)
        if len(episodes) != 1:
            report['status'] = 'PENDING_EPISODE'
            return
        episode = report['episode'] = episodes[0]
        path = '/api/v1/user/ops/task-acceptance/' + episode['episodeId'] + '?projectId=' + PROJECT
        before = report['before'] = h['api'](path, token=h['token'])
        if before.get('outcome') == 'SUCCEEDED':
            for history in before.get('history', []):
                raw = history.get('record_json', '')
                if hashlib.sha256(raw.encode()).hexdigest() != history.get('record_hash'):
                    continue
                record = json.loads(raw)
                if (record.get('sourceRunId') == run and record.get('projectId') == PROJECT
                        and record.get('episodeId') == episode['episodeId'] and record.get('revision') == episode['revision']
                        and record.get('outcome') == 'SUCCEEDED' and valid_checks(record.get('checks', []), True)):
                    report['status'] = 'ALREADY_VERIFIED_REVISION'
                    return
            raise ValueError('Existing acceptance differs; do not overwrite or re-accept it')
        details = plan_descriptions(sql['plan'])
        instruction = (f"请逐项核验本次两类独立只读任务。目录回执实际匹配数为1，服务是{directory['serviceId']}，版本{directory['version']}，状态{directory['state']}。"
                       f"数据库目标订单为{sql['orderId']}。项目{PROJECT}、逻辑环境acceptance、服务{sql['scope']['serviceId']}及回执AVAILABLE属于已由平台和独立原生资源核验的归属背景，不为scope、status等回执信封字段建立业务结果条件。"
                       "真实优化器逐表字段为：" + '；'.join(details) + "。请为上述每个业务字段建立等值验收条件，各自引用对应本次工具回执；只验收本次读取和执行计划，不证明修复、持续健康或性能收益。")
        report['reviewerInstruction'] = instruction
        draft = report['draft'] = h['api'](path.replace('?projectId=', '/draft?projectId='), 'POST',
                                            {'revision': episode['revision'], 'instruction': instruction}, h['token'], timeout=720)
        if draft.get('status') != 'READY':
            report['status'] = 'DRAFT_NOT_READY'
            return
        if not valid_checks(draft['request']['criteria']):
            report['status'] = 'DRAFT_REQUIRES_REVIEW'
            return
        verification = report['verification'] = h['api'](path, 'POST', draft['request'], h['token'], timeout=120)
        report['after'] = h['api'](path, token=h['token'])
        assert verification['outcome'] == report['after']['outcome'] == 'SUCCEEDED'
        assert valid_checks(verification['checks'], True)
        assert all(c.get('verdict') == 'PASSED' for c in verification['checks'])
        report['status'] = 'PASS_ACTUAL_COMPOSITE_ACCEPTANCE'
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({key: report.get(key) for key in ('status', 'runId', 'pair', 'failureType')}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    accept(args.evidence, args.output)
