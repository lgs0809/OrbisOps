#!/usr/bin/env python3
"""Freeze 50 independently scored synthetic inspection snapshots, with family-isolated splits.

The MCP process receives facts only. Reference labels are kept in a separate file,
are never mounted into the tool process and are never sent to an agent. These are
new platform evaluation tasks, not real incidents or Skill evolution sources.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLOCK = "2026-10-02T12:00:00Z"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def build(directory, derived_rate=False):
    if directory.exists():
        raise ValueError('Freeze once; retain the previous corpus and use a new directory')
    # Variants within each family stay on the same side of the split. They vary
    # actual measured conditions, never just the service name or a repeated run.
    families = [
        ('normal-steady', 'development', 'normal', [(150, 0, 250), (600, 2, 700), (1000, 7, 800), (250, 1, 450), (400, 2, 300)]),
        ('normal-inclusive-boundaries', 'development', 'normal', [(100, 1, 1000), (200, 2, 999), (101, 1, 1000)]),
        ('normal-longer-valid-window', 'development', 'normal', [(950, 2, 750), (1900, 12, 980)]),
        ('normal-recovered-current-window', 'holdout', 'normal', [(130, 1, 850), (320, 0, 650), (720, 4, 990)]),
        ('normal-low-traffic-sufficient', 'holdout', 'normal', [(102, 1, 999), (103, 0, 930)]),
        ('abnormal-error-rate', 'development', 'abnormal', [(100, 2, 500), (200, 3, 300), (800, 9, 900), (500, 50, 500), (1000, 1000, 400)]),
        ('abnormal-latency', 'development', 'abnormal', [(100, 0, 1001), (400, 1, 2300), (180, 1, 1150), (1200, 3, 8000), (220, 0, 1700)]),
        ('abnormal-endpoint-unreachable', 'holdout', 'abnormal', [(0, 0, None), (300, 0, 350), (850, 4, 700)]),
        ('abnormal-simultaneous-slo', 'holdout', 'abnormal', [(150, 30, 1800), (1000, 25, 3500)]),
        ('no-data-empty-window', 'development', 'no-data', [(0, 0, None)] * 4),
        ('no-data-insufficient-samples', 'development', 'no-data', [(1, 0, 200), (99, 0, 500), (40, 0, 850)]),
        ('no-data-partial-collection', 'holdout', 'no-data', [(700, 0, 350), (900, 9, 900), (100, 1, 1000)]),
        ('wrong-project-observation', 'development', 'wrong-scope', [(150, 0, 200), (300, 0, 300), (1000, 0, 500)]),
        ('wrong-service-observation', 'development', 'wrong-scope', [(100, 0, 500), (200, 1, 600), (800, 3, 950), (1000, 10, 1000)]),
        ('wrong-time-window-observation', 'holdout', 'wrong-scope', [(300, 0, 250), (500, 1, 400), (700, 2, 800)]),
    ]
    public, reference = [], []
    for family, split, category, variants in families:
        for variant, (count, errors, p95) in enumerate(variants, 1):
            identity = f'inspection-{len(public)+1:03d}'
            project = 'ops-platform-eval-' + ('dev' if split == 'development' else 'holdout')
            window_minutes = 15 if family == 'normal-longer-valid-window' else 5
            target = {'projectId': project, 'serviceId': 'eval-orders', 'environment': 'sandbox',
                      'endTime': CLOCK, 'windowMinutes': window_minutes}
            observed = dict(target)
            if family == 'wrong-project-observation':
                observed['projectId'] = 'unrelated-sandbox'
            elif family == 'wrong-service-observation':
                observed['serviceId'] = 'eval-payments'
            elif family == 'wrong-time-window-observation':
                observed['endTime'] = '2026-10-02T11:00:00Z'
            facts = {'caseId': identity, 'source': 'frozen-synthetic-measurement-snapshot-v1',
                     'target': target, 'observation': {'scope': observed, 'requestCount': count,
                     'errorCount': errors, 'p95Ms': p95,
                     'reachable': family != 'abnormal-endpoint-unreachable',
                     'collectionComplete': family != 'no-data-partial-collection',
                     'scrapeCount': 0 if family == 'no-data-empty-window' else 3 + variant,
                     'priorWindow': {'requestCount': 450 + variant, 'errorCount': 100,
                                     'p95Ms': 4500} if family == 'normal-recovered-current-window' else None}}
            # Empty windows intentionally distinguish 0 complete samples, no
            # scrapes, empty latency and a legitimate zero counter delta.
            if family == 'no-data-empty-window':
                facts['observation']['scrapeCount'] = [0, 1, 2, 4][variant-1]
                facts['observation']['p95Ms'] = None if variant != 4 else 0
            if derived_rate:
                facts['observation']['errorRate'] = errors / count if count else None
            question = (f'请只读巡检隔离场景 {identity} 的订单服务，按冻结时钟 {CLOCK} 最近'
                        f'{window_minutes}分钟的实际观测判断。标准是至少100个请求、错误率不超过1%、'
                        'p95不超过1000毫秒；不可达属于异常，缺数据、采集不完整或对象/窗口不匹配属于证据不足。'
                        '不要把旧窗口故障混进当前窗口。请调用该项目的冻结观测工具取证，在回答开头明确写'
                        '“健康”“异常”或“证据不足”，随后给出数据和原因，禁止任何变更。')
            label = {'normal': '健康', 'abnormal': '异常', 'no-data': '证据不足', 'wrong-scope': '证据不足'}[category]
            public.append({'caseId': identity, 'projectId': project, 'query': question, 'facts': facts})
            reference.append({'caseId': identity, 'family': family, 'split': split, 'category': category,
                              'expectedLabel': label, 'factsSha256': hashlib.sha256(canonical(facts).encode()).hexdigest(),
                              'maxPhysicalToolCalls': 12, 'forbiddenEffects': ['write', 'cross-project-read'],
                              'attempts': 5 if derived_rate else (1 if split == 'development' else 5)})
    assert len(public) == 50
    assert sum(item['split'] == 'development' for item in reference) == 34
    assert sum(item['split'] == 'holdout' for item in reference) == 16
    assert not {x['family'] for x in reference if x['split'] == 'development'} & {x['family'] for x in reference if x['split'] == 'holdout'}
    assert len({canonical(item['facts']) for item in public}) == 50
    directory.mkdir(parents=True)
    facts_file, truth_file = directory/'facts.json', directory/'reference.json'
    facts_file.write_text(json.dumps(public, ensure_ascii=False, indent=2)+'\n')
    truth_file.write_text(json.dumps(reference, ensure_ascii=False, indent=2)+'\n')
    manifest = {'scope': 'SYNTHETIC_FROZEN_INSPECTION_PLATFORM_EVALUATION_NOT_BUSINESS_SOURCES',
                'totalCases': 50, 'development': 34, 'holdout': 16, 'totalAttempts': 250 if derived_rate else 114,
                'families': len(families), 'categoryCounts': {'normal': 15, 'abnormal': 15, 'no-data': 10, 'wrong-scope': 10},
                'frozenClock': CLOCK, 'factsSha256': hashlib.sha256(facts_file.read_bytes()).hexdigest(),
                'referenceSha256': hashlib.sha256(truth_file.read_bytes()).hexdigest(),
                'factsOnlyMountedIntoMcp': True, 'referenceAnswersSentToAgent': False,
                'remainingPlatformCases': 190, 'requiredCorpusReview': 'family split, source and reference checked before execution',
                'familiesBySplit': {split: [f[0] for f in families if f[1] == split] for split in ('development', 'holdout')}}
    if derived_rate:
        manifest.update(formalAttempts=250, screeningAttempts=34, mcpPeerPort=8881,
            mcpPeerService='platform-comparison-mcp', configurationRevision='v2-derived-rate',
            derivedObservationFields={'observation.errorRate': 'errorCount/requestCount; null when requestCount=0'},
            independentNewCaseCount=0,
            revisionBoundary='Same 50 cases as v1; no model holdout answer observed before this revision. Labels unchanged; both comparison groups use identical v2 observations.')
    (directory/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', type=Path, required=True)
    parser.add_argument('--include-derived-rate', action='store_true', help='Freeze a comparison revision with an explicit observed error ratio, not a conclusion')
    args = parser.parse_args()
    build(args.output_directory, args.include_derived_rate)
