#!/usr/bin/env python3
"""Check deployed atomic-publication access and full accepted-source archival.

No candidate, approval, acceptance or publication outcome is manufactured.
Only read queries and deliberately unauthorized requests are made.
"""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(output, source_id):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new filename; preserve previous evidence')
    r = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = r['quoted'](source_id)
    queries = {
        'source': "SELECT JSON_OBJECT('sourceId',source_id,'bytes',OCTET_LENGTH(input_json),"
                  "'characters',CHAR_LENGTH(input_json),'hash',input_hash,'actualHash',SHA2(input_json,256),"
                  "'receiptCount',JSON_LENGTH(input_json,'$.receipts'),'episodeId',episode_id) "
                  "FROM ai_ops_skill_evolution_source WHERE source_id=" + q,
        'job': "SELECT JSON_OBJECT('jobId',j.job_id,'status',j.status,'attempts',j.attempts,"
               "'lastError',j.last_error,'nextRunAt',j.next_run_at) FROM ai_ops_skill_evolution_job j "
               "JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id WHERE s.source_id=" + q,
        'audit': "SELECT JSON_OBJECT('at',a.create_time,'state',a.result_status,'detail',CAST(a.after_json AS JSON)) "
                 "FROM ai_ops_config_audit a JOIN ai_ops_skill_evolution_job_state s ON s.job_id=a.target_id "
                 "WHERE a.module_name='skill-evolver' AND a.action_name='job-fail' AND s.source_id=" + q +
                 " ORDER BY a.id DESC LIMIT 1",
        'publications': "SELECT JSON_OBJECT('candidateId',candidate_id,'projectId',project_id,'state',status,"
                        "'hash',plan_hash,'actualHash',SHA2(plan_json,256)) FROM ai_ops_skill_atomic_publication ORDER BY candidate_id",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries.values()) + ';\n')
    evidence = {'status': 'RUNNING', 'boundary': 'Archival and access checks only; no real model publication is claimed.'}
    try:
        evidence.update({name: r['rows'](query) for name, query in queries.items()})
        assert len(evidence['source']) == 1 and len(evidence['job']) == 1
        source = evidence['source'][0]
        assert source['hash'] == source['actualHash'] and source['receiptCount'] > 0
        path = '/api/v1/admin/ops/skill-evolver/atomic-publications'
        evidence['adminRecords'] = r['api'](path + '?projectId=ops-acceptance-a', token=r['token'])
        users = json.loads((ROOT / 'deploy/.acceptance-private/users.json').read_text())
        evidence['permissions'] = {}
        for username in ('ops_acceptance_viewer', 'ops_acceptance_b_member', 'ops_acceptance_operator'):
            token = r['api_module']['login'](users[username])
            read = r['api'](path, token=token, denied=True)
            write = r['api'](path + '/nonexistent-permission-probe/rollback', 'POST',
                             {'projectId': 'ops-acceptance-a', 'reason': '未授权请求必须被拦截'}, token, denied=True)
            assert read['httpStatus'] == 403 and write['httpStatus'] == 403
            evidence['permissions'][username] = {'read': 403, 'rollback': 403}
        assert r['rows'](queries['publications']) == evidence['publications']
        evidence['status'] = 'PASS_INTEGRITY_AND_ACCESS'
    except Exception as error:
        evidence['status'] = 'FAIL'
        evidence['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': evidence['status'], 'sourceBytes': source['bytes'],
                      'jobState': evidence['job'][0]['status'], 'realPublicationCount': len(evidence['publications'])}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source-id', required=True)
    args = parser.parse_args()
    inspect(args.output, args.source_id)
