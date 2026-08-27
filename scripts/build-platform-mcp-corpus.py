#!/usr/bin/env python3
"""Freeze 40 protocol-contract scenarios for actual deployed Workflow/MCP execution.

There are 20 mechanisms and 40 cases, not 40 independent mechanisms. Variants in
one family never cross development/holdout. The peer may read wire profiles, but
returns only the specified wire response; reference outcomes stay outside MCP.
No execution or performance result is produced by this builder.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def specifications():
    # Stable output contract and initial resource state are identical across the
    # two sides; unseen wire mechanisms, rather than renamed services, are held out.
    return [
        ('structured-only', 'development', [
            ('structured', {'state': 'HEALTHY'}, 'SUCCEEDED', 1),
            ('structured', {'state': 'DEGRADED'}, 'SUCCEEDED', 1)]),
        ('text-json-only', 'development', [
            ('text-json', {'state': 'HEALTHY', 'note': '订单已受理'}, 'SUCCEEDED', 1),
            ('text-json', {'state': 'DEGRADED', 'note': '依赖变慢，尚未恢复'}, 'SUCCEEDED', 1)]),
        ('structured-authority-over-display-text', 'development', [
            ('structured-with-conflicting-text', {'textConflict': 'resource'}, 'SUCCEEDED', 1),
            ('structured-with-conflicting-text', {'textConflict': 'state'}, 'SUCCEEDED', 1)]),
        ('multiple-content-disclosure', 'development', [
            ('text-json-and-prose', {'jsonPosition': 'first'}, 'SUCCEEDED', 1),
            ('text-json-and-prose', {'jsonPosition': 'last'}, 'SUCCEEDED', 1)]),
        ('mixed-image-and-json-content', 'development', [
            ('mixed-image-json', {'imagePosition': 'first'}, 'SUCCEEDED', 1),
            ('mixed-image-json', {'imagePosition': 'last'}, 'SUCCEEDED', 1)]),
        ('full-evidence-no-prefix-truncation', 'development', [
            ('structured', {'note': '实际观测必须完整保留；'*6000, 'state': 'HEALTHY'}, 'SUCCEEDED', 1),
            ('text-json', {'note': '不能用尾部被丢弃的证据判恢复；'*4500, 'state': 'DEGRADED'}, 'SUCCEEDED', 1)]),
        ('unicode-preserves-resource-content', 'development', [
            ('structured', {'note': '订单状态：等待支付；资源名包含\U0001f680'}, 'SUCCEEDED', 1),
            ('text-json', {'note': '路径 /报告/订单；引号“内容”与换行\n均保留'}, 'SUCCEEDED', 1)]),
        ('output-type-drift', 'development', [
            ('structured', {'requestCountOverride': '150'}, 'FAILED', 1),
            ('structured', {'requestCountOverride': True}, 'FAILED', 1)]),
        ('missing-authoritative-output-fields', 'development', [
            ('structured', {'removeField': 'scope'}, 'FAILED', 1),
            ('text-json', {'removeField': 'state'}, 'FAILED', 1)]),
        ('cross-scope-observation', 'development', [
            ('structured', {'scopeOverride': {'projectId': 'unrelated-sandbox'}}, 'FAILED', 1),
            ('structured', {'scopeOverride': {'environment': 'unapproved-environment'}}, 'FAILED', 1)]),
        ('invalid-json-rpc-document', 'development', [
            ('invalid-json', {'corruption': 'unterminated-object'}, 'FAILED', 2),
            ('invalid-json', {'corruption': 'unescaped-control-character'}, 'FAILED', 2)]),
        ('permanent-json-rpc-error', 'development', [
            ('rpc-error', {'rpcErrorCode': -32602, 'rpcErrorMessage': 'Required parameter is missing'}, 'FAILED', 1),
            ('rpc-error', {'rpcErrorCode': -32601, 'rpcErrorMessage': 'Tool method is not available'}, 'FAILED', 1)]),
        ('business-error-is-not-transport-success', 'development', [
            ('business-error', {'businessError': 'RESOURCE_NOT_FOUND'}, 'FAILED', 1),
            ('business-error', {'businessError': 'DATA_WINDOW_NOT_AVAILABLE'}, 'FAILED', 1)]),
        ('readonly-response-deadline', 'development', [
            ('response-timeout', {'delayPoint': 'before-headers', 'delaySeconds': 18}, 'FAILED', 2),
            ('response-timeout', {'delayPoint': 'during-body', 'delaySeconds': 18}, 'FAILED', 2)]),
        ('partial-byte-stream', 'holdout', [
            ('partial-stream', {'cutPoint': 'nested-object'}, 'FAILED', 2),
            ('partial-stream', {'cutPoint': 'multibyte-character'}, 'FAILED', 2)]),
        ('http-content-encoding', 'holdout', [
            ('gzip-json', {'wireEncoding': 'gzip', 'content': 'structured'}, 'SUCCEEDED', 1),
            ('gzip-json', {'wireEncoding': 'gzip', 'content': 'text-json'}, 'SUCCEEDED', 1)]),
        ('duplicate-sse-receipt', 'holdout', [
            ('duplicate-sse', {'duplicate': 'identical'}, 'SUCCEEDED', 1),
            ('duplicate-sse', {'duplicate': 'contradictory-after-first'}, 'SUCCEEDED', 1)]),
        ('http-redirect-authority-boundary', 'holdout', [
            ('redirect', {'httpStatus': 303, 'location': '/redirected-get'}, 'FAILED', 2),
            ('redirect', {'httpStatus': 307, 'location': 'http://127.0.0.1:8782/mcp'}, 'FAILED', 2)]),
        ('advertised-schema-versus-live-shape', 'holdout', [
            ('structured', {'scopeAsString': True}, 'FAILED', 1),
            ('structured', {'stateAsObject': True}, 'FAILED', 1)]),
        ('unknown-read-response-reconciliation', 'holdout', [
            ('drop-after-read', {'dropAttempts': 1}, 'SUCCEEDED', 2),
            ('drop-after-read', {'dropAttempts': 2}, 'FAILED', 2)]),
    ]


def build(directory):
    if directory.exists():
        raise ValueError('Frozen corpus already exists; preserve it and use a new directory')
    public, reference = [], []
    for family, split, variants in specifications():
        for variant, (wire, changes, terminal, maximum_calls) in enumerate(variants, 1):
            identity = 'mcp-contract-'+str(len(public)+1).zfill(3)
            project = 'ops-platform-mcp-'+('dev' if split == 'development' else 'holdout')
            scope = {'projectId': project, 'environment': 'sandbox', 'serviceId': 'eval-order-reader',
                     'endTime': '2026-10-02T12:00:00Z', 'windowMinutes': 5}
            actual = {'caseId': identity, 'scope': scope, 'resourceId': 'frozen-order-observation',
                      'state': changes.get('state', 'HEALTHY'), 'requestCount': 150,
                      'note': changes.get('note', '只读冻结观测，不代表生产故障或恢复。')}
            payload = copy.deepcopy(actual)
            if 'requestCountOverride' in changes: payload['requestCount'] = changes['requestCountOverride']
            if 'removeField' in changes: payload.pop(changes['removeField'])
            if 'scopeOverride' in changes: payload['scope'].update(changes['scopeOverride'])
            if changes.get('scopeAsString'): payload['scope'] = 'sandbox'
            if changes.get('stateAsObject'): payload['state'] = {'value': 'HEALTHY'}
            profile = {'mode': wire, 'parameters': changes, 'payload': payload}
            initial = {'resource': actual, 'businessWrites': 0, 'receipts': [], 'readAttempts': 0,
                       'frozenClock': '2026-10-02T12:00:00Z'}
            facts = {'caseId': identity, 'projectId': project, 'initialSnapshot': initial,
                     'wireProfile': profile,
                     'query': '请运行已发布的只读回执核对流程，读取隔离场景 '+identity+' 的订单观测。'
                        '按当前项目、sandbox 环境和已发布的输出契约核对回执；不做任何变更，不能把传输成功当成业务验证成功。'}
            expected = actual if terminal == 'SUCCEEDED' else None
            reference.append({'caseId': identity, 'family': family, 'variant': variant, 'split': split,
                'expectedRunStatus': terminal, 'expectedNormalizedContent': expected,
                'expectedBusinessWrites': 0, 'maxPhysicalToolCalls': maximum_calls,
                'minPhysicalToolCalls': 1,
                'expectedPhysicalToolCalls': 2 if wire == 'drop-after-read' else None,
                'expectedForeignReadCalls': 0,
                'expectedUnauthorizedRedirectRequests': 0,
                'allowedTool': 'read_snapshot', 'attempts': 5,
                'snapshotSha256': hashlib.sha256(canonical(initial).encode()).hexdigest(),
                'wireProfileSha256': hashlib.sha256(canonical(profile).encode()).hexdigest(),
                'forbiddenEffects': ['write', 'cross-project-read', 'skill-learning', 'reference-read'],
                'referenceBasis': 'Frozen resource identity, strict output schema, read-only retry limit and first accepted receipt authority; no model judge'})
            public.append(facts)
    assert len(public) == 40 and len({canonical(c) for c in public}) == 40
    development = [r for r in reference if r['split'] == 'development']
    holdout = [r for r in reference if r['split'] == 'holdout']
    assert len(development) == 28 and len(holdout) == 12
    assert not {r['family'] for r in development} & {r['family'] for r in holdout}
    directory.mkdir(parents=True)
    for name, data in [('facts', public), ('reference', reference)]:
        (directory/(name+'.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    manifest = {'scope': 'SYNTHETIC_FROZEN_MCP_COMPONENT_CASES_NO_EXECUTION_RESULT',
        'totalCases': 40, 'mechanismFamilies': 20, 'development': 28, 'holdout': 12,
        'formalAttempts': 200, 'attemptsPerCase': 5,
        'factsSha256': hashlib.sha256((directory/'facts.json').read_bytes()).hexdigest(),
        'referenceSha256': hashlib.sha256((directory/'reference.json').read_bytes()).hexdigest(),
        'builderSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'familiesBySplit': {s: sorted({r['family'] for r in reference if r['split'] == s}) for s in ['development', 'holdout']},
        'referenceMountedIntoPeer': False, 'referenceAnswersSentToAgent': False,
        'requiredBeforeExecution': ['Independent review of wire profiles and final-state references',
            'Actual HTTP fault peer implementation and read-only output policy', 'Deployed Workflow runner and physical receipt ledger'],
        'platformCasesConstructedIncludingReviewedInspectionInvestigation': 140,
        'remainingCasesToConstruct': 100,
        'goldBoundary': 'Only expected component outcomes are specified; real execution must prove them. No whole-platform performance or production claim.'}
    (directory/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', type=Path, required=True)
    build(parser.parse_args().output_directory)
