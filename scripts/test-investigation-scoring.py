#!/usr/bin/env python3
"""Static rubric boundaries only. These are not platform/model evaluation cases."""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path

MODULE = Path(__file__).with_name('platform-investigation-scoring.py')
spec = importlib.util.spec_from_file_location('investigation_scoring', MODULE)
scoring = importlib.util.module_from_spec(spec); spec.loader.exec_module(scoring)
BOUNDARIES = [
    ('CARDINALITY_ESTIMATION', '陈旧统计导致嵌套循环严重低估，放大延迟。', True),
    ('CARDINALITY_ESTIMATION', '谓词相关性导致严重行数低估。', True),
    ('CARDINALITY_ESTIMATION', '基数估算严重失准造成扫描过大；不能确认生产根因。', True),
    ('CARDINALITY_ESTIMATION', '证据不能确认基数估算偏差。', False),
    ('CARDINALITY_ESTIMATION', '估算偏差未获支持。', False),
    ('CONNECTION_NOT_RETURNED', '取消请求未归还连接导致池耗尽。', True),
    ('CONNECTION_NOT_RETURNED', '请求结束后连接未被及时归还，导致排队。', True),
    ('CONNECTION_NOT_RETURNED', '没有证据表明连接未归还。', False),
    ('CONNECTION_NOT_RETURNED', '排除连接泄漏，当前支持长事务。', False),
    ('CONNECTION_NOT_RETURNED', '可能存在连接泄漏；尚不能确认生产根因。', True),
    ('POOL_CAPACITY', '连接池已饱和并形成排队；未能归因到SQL或依赖。', True),
    ('POOL_CAPACITY', '连接池满载；不足以归因到SQL、锁或下游依赖。', True),
    ('POOL_CAPACITY', '无法确定连接池饱和。', False),
    ('POOL_CAPACITY', '连接未归还导致连接池耗尽。', False),
    ('POOL_CAPACITY', '连接池满载；没有证据支持连接泄漏。', True),
    ('QUERY_ACCESS_PATH', '支持全表扫描；无法确定是否有CPU压力。', True),
    ('QUERY_ACCESS_PATH', '不是全表扫描导致的延迟。', False),
    ('QUERY_ACCESS_PATH', '支持访问路径低效，但又排除访问路径低效。', False),
    ('UNDETERMINED', '观测冲突，目前证据不足以判断故障机制。', True),
    ('UNDETERMINED', '证据不足，不能确认DNS解析失败或CPU压力。', True),
    ('UNDETERMINED', '证据不足，但主要支持DNS解析失败。', False),
    ('DNS_RESOLUTION', 'DNS解析失败；排除TLS证书校验故障。', True),
    ('DNS_RESOLUTION', 'DNS正常；支持TLS证书校验故障。', False),
    ('DATABASE_LOCK_WAIT', 'DATABASE_LOCK_WAIT是当前最强观测解释。', True),
    ('CONNECTION_NOT_RETURNED', '不存在连接泄漏。', False),
    ('CONNECTION_NOT_RETURNED', '连接泄漏已经排除。', False),
    ('POOL_CAPACITY', '连接池容量充足。', False),
    ('DNS_RESOLUTION', 'DNS解析正常。', False),
    ('DNS_RESOLUTION', '没有DNS问题。', False),
    ('QUERY_ACCESS_PATH', '访问路径选择正常。', False),
    ('CONNECTION_NOT_RETURNED', '已确认生产根因是连接泄漏。', False),
    ('TLS_VALIDATION', 'TLS验证成功，未发现证书异常。', False),
    ('NETWORK_PRESSURE', '网络压力很低，整体工作正常。', False),
    ('CONNECTION_NOT_RETURNED', '并非CPU压力而是连接未归还造成排队。', True),
    ('CPU_PRESSURE', '不能确定网络压力，但支持CPU压力导致排队。', True),
    ('CONNECTION_NOT_RETURNED', '连接未归还造成排队；不能断言已确认生产根因。', True),
    ('CONNECTION_NOT_RETURNED', '连接未归还问题不存在。', False),
    ('DNS_RESOLUTION', 'DNS无异常，当前证据支持TLS证书校验故障。', False),
    ('DNS_RESOLUTION', '未发现DNS异常。', False),
    ('DNS_RESOLUTION', 'DNS并无异常。', False),
    ('CONNECTION_NOT_RETURNED', '未检测到连接泄漏。', False),
    ('CONNECTION_NOT_RETURNED', '连接泄漏未发现。', False),
    ('DNS_RESOLUTION', '待进一步核实DNS是否异常。', False),
    ('DNS_RESOLUTION', '建议排查DNS，现有证据尚不足以确认。', False),
    ('DNS_RESOLUTION', '生产根因确定为DNS解析故障。', False),
    ('CPU_PRESSURE', '未识别CPU压力；证据支持连接未归还。', False),
    ('DNS_RESOLUTION', 'DNS。', False),
    ('DNS_RESOLUTION', '观测支持DNS解析失败；具体缓存TTL仍需核实。', True),
    ('TLS_VALIDATION', '证书过期导致TLS验证失败；不能确认生产根因。', True),
    ('QUERY_ACCESS_PATH', '支持全表扫描；该结论不构成生产根因确认。', True),
    ('CARDINALITY_ESTIMATION', '支持基数估算偏差；不能据此宣称已验证生产根因。', True),
    ('CONNECTION_NOT_RETURNED', '支持连接未归还；这不代表已确认生产根因。', True),
    ('POOL_CAPACITY', '支持连接池满载；这不等同于生产根因确定。', True),
    ('DNS_RESOLUTION', '支持DNS解析失败；生产根因已验证。', False),
    ('DNS_RESOLUTION', '支持DNS解析失败。', True),
    ('DNS_RESOLUTION', '支持DNS解析失败；这不等于生产根因确认。', True),
    ('DNS_RESOLUTION', '支持DNS解析失败；该结论不意味着生产根因已证实。', True),
    ('DNS_RESOLUTION', '支持DNS解析失败；生产根因未被证实。', True),
    ('DNS_RESOLUTION', '支持DNS解析失败；生产根因已被确认。', False),
    ('DNS_RESOLUTION', '证据不支持DNS解析失败；生产根因也不能确认。', False),
]


def main(output):
    rows = []
    for expected, finding, wanted in BOUNDARIES:
        answer = {'finding': finding, 'reason': '静态规则边界：不宣称存在真实观测。', 'confirmedProductionRootCause': False}
        actual = scoring.score(answer, expected)
        rows.append({'expectedMechanism': expected, 'finding': finding, 'wanted': wanted, 'actual': actual,
            'status': 'PASS' if actual['passed'] == wanted else 'FAIL'})
    for change, wanted in [({'confirmedProductionRootCause': True}, False), ({'reason': ''}, False)]:
        answer = {'finding': '连接池满载。', 'reason': '静态说明。', 'confirmedProductionRootCause': False, **change}
        actual = scoring.score(answer, 'POOL_CAPACITY')
        rows.append({'answer': answer, 'wanted': wanted, 'actual': actual, 'status': 'PASS' if actual['passed'] == wanted else 'FAIL'})
    report = {'scope': 'STATIC_SCORER_BOUNDARY_CHECKS_NOT_PLATFORM_PERFORMANCE', 'createdAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'version': scoring.VERSION, 'scorerSha256': hashlib.sha256(MODULE.read_bytes()).hexdigest(),
        'boundaryScriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'checks': len(rows), 'passed': sum(r['status'] == 'PASS' for r in rows), 'results': rows}
    if output.exists(): raise RuntimeError('Retain previous boundary evidence; choose a new output')
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k != 'results'},ensure_ascii=False))
    if report['passed'] != report['checks']: raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args().output)
