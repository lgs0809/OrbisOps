#!/usr/bin/env python3
"""Review an existing successful observation through normal acceptance APIs.

No business workflow is re-executed. An interrupted host orchestrator remains
recorded independently; native receipts, episode revision and model-produced
criteria are checked before normal confirmation. This is not a new source.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import runpy
import time

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-closure-business-a'


def reconcile(run, service, prior, output, expected_jar):
    if output.exists():
        raise ValueError('Retain previous reconciliation evidence')
    h = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    api, token, q = h['api'], h['token'], h['quoted']
    prior_bytes = prior.read_bytes()
    original = json.loads(prior_bytes)
    retained = next(r for r in original['results'] if r.get('runId') == run and r['service'] == service)
    snapshot = output.with_name(output.stem+'-prior-snapshot.json')
    if snapshot.exists():
        raise ValueError('Retain previous orchestration snapshot')
    snapshot.write_bytes(prior_bytes)
    report = {'status': 'CHECKING_NATIVE_RUN', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'runId': run, 'service': service, 'newWorkflowExecutions': 0, 'newIndependentSources': 0,
        'priorEvidence': {'path': str(snapshot), 'liveOrchestratorPath': str(prior), 'sha256': hashlib.sha256(prior_bytes).hexdigest(),
            'originalStatus': retained['status'], 'originalError': retained.get('error')},
        'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'deployedJarSha256': expected_jar,
        'artifactBoundary': {
            'originalWorkflowExecutionJarSha256': original.get('deployedJarSha256'),
            'currentAcceptanceVerifierJarSha256': expected_jar,
            'originalWorkflowReexecuted': False,
            'originalReceiptsReused': True}}

    def save():
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')

    def until(check, seconds=480):
        end = time.monotonic()+seconds
        while time.monotonic() < end:
            result = check()
            if result:
                return result
            time.sleep(2)
        raise TimeoutError('Native episode is not assigned')

    save()
    try:
        report['nativeRun'] = h['facts'](run)
        if report['nativeRun']['status'] != 'SUCCEEDED':
            raise ValueError('Original actual workflow has not succeeded')
        spec = importlib.util.spec_from_file_location('actual_closure_truth', ROOT/'scripts/inspect-closure-observation.py')
        truth = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(truth)
        proof_path = output.with_name(output.stem+'-proof.json')
        proof = truth.inspect(run, service, proof_path)
        report['truthEvidence'] = str(proof_path)
        if proof['status'] != 'PASS' or proof['deployedJarSha256'] != expected_jar:
            raise ValueError('Actual tool, HTTP truth or artifact is not proven')

        def assigned():
            rows = h['rows']("SELECT JSON_OBJECT('episodeId',e.episode_id,'revision',e.revision,'outcome',e.outcome) FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id WHERE t.project_id="+q(PROJECT)+" AND t.status='ASSIGNED' AND t.source_run_ref="+q(run))
            if len(rows) > 1:
                raise ValueError('Ambiguous native episode')
            return rows[0] if rows else None

        episode = report['episode'] = until(assigned)
        address = '/api/v1/user/ops/task-acceptance/'+episode['episodeId']
        suffix = '?projectId='+PROJECT
        report['before'] = api(address+suffix, token=token)
        observation = next(r for r in proof['receipts'] if 'beforeState' in json.loads(r['output']).get('normalizedContent', {}))
        content = json.loads(observation['output'])['normalizedContent']
        expected = {pointer: content[key] for pointer, key in [('/projectId', 'projectId'),
            ('/environment', 'environment'), ('/resourceKey', 'resourceKey'), ('/status', 'status'),
            ('/observationStart', 'observationStart'), ('/observationEnd', 'observationEnd'),
            ('/configurationUnchanged', 'configurationUnchanged'), ('/requestCount', 'requestCount'),
            ('/errorCount', 'errorCount'), ('/allRequestsSucceeded', 'allRequestsSucceeded'),
            ('/requestMethod', 'requestMethod'), ('/routeDefinition', 'routeDefinition')]}
        for phase in ['beforeState', 'afterState']:
            for field in ['version', 'epoch', 'configurationDigest']:
                expected['/'+phase+'/'+field] = content[phase][field]
        review = f'请继续核验原定资源观察目标，不执行新的观察任务。项目{PROJECT}、test环境、资源service://{service}/test，仅覆盖本次真实observationStart至observationEnd。原脚本因资源调度暂停超时的记录保留，原生运行成功不等于验收。请根据实际回执保留全部具体条件：项目、环境、资源身份、status PASSED、真实开始结束区间、configurationUnchanged true、20次GET /orders全部HTTP200且错误数0，以及beforeState和afterState各自version observation-initial、epoch0、configurationDigest一致。请求方法和路径用已有requestMethod GET、routeDefinition /orders；全部HTTP状态用实际20条状态重算的allRequestsSucceeded true标量，不需要20条重复条件。保留18项实际证据绑定，不能缩窄目标、不能声称区间外或其他资源没变。'
        report.update(status='DRAFT_REQUESTED', reviewerInstruction=review, requiredBusinessFields=expected)
        save()
        draft = report['draft'] = api(address+'/draft'+suffix, 'POST',
            {'revision': episode['revision'], 'instruction': review}, token, timeout=720)
        save()
        if draft['status'] != 'READY':
            report['status'] = 'DRAFT_NOT_READY'
            return
        criteria = draft['request']['criteria']
        checks = {pointer: any(c['pointer'] == pointer and c['operator'] == 'EQ' and c['expected'] == value
            and c['resultId'] == observation['resultId'] and c['outputHash'] == observation['hash'] for c in criteria)
            for pointer, value in expected.items()}
        report['criteriaReview'] = checks
        if not all(checks.values()):
            report['status'] = 'DRAFT_REQUIRES_REVIEW'
            return
        report['status'] = 'CONFIRMATION_REQUESTED_RESPONSE_UNCONFIRMED'
        save()
        result = report['verification'] = api(address+suffix, 'POST', draft['request'], token, timeout=120)
        report['after'] = api(address+suffix, token=token)
        report['status'] = 'PASS_ACTUAL_ACCEPTANCE_SAME_RUN' if result['outcome'] == 'SUCCEEDED' and \
            report['after']['outcome'] == 'SUCCEEDED' and all(c['verdict'] == 'PASSED' for c in result['checks']) \
            else 'BUSINESS_VERIFICATION_FAILED'
    except Exception as error:
        report['interruptedStage'] = report['status']
        report.update(status='FAILED_OR_UNCONFIRMED', error=type(error).__name__+': '+str(error))
        raise
    finally:
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        save()
        output.with_suffix('.sql').write_text('\n'.join(h['sql_queries'])+'\n')
        print(json.dumps({'status': report['status'], 'runId': run, 'newWorkflowExecutions': 0}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', required=True)
    parser.add_argument('--service', required=True)
    parser.add_argument('--prior', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--expected-jar', required=True)
    args = parser.parse_args()
    reconcile(args.run, args.service, args.prior, args.output, args.expected_jar)
