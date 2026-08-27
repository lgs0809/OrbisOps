#!/usr/bin/env python3
"""Freeze heterogeneous open-investigation sandbox facts; keep references outside the MCP mount.

Explicit observable mechanisms vary SQL access paths, estimates, locking, pool
ownership, dependency protocols, CPU/GC/disk/network and evidence completeness.
Changing service names alone never creates a new case. These are newly constructed
synthetic snapshots, never production incidents or Skill evolution sources.
"""
import argparse
import hashlib
import json
from pathlib import Path


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def build(directory):
    if directory.exists():
        raise ValueError('Retain frozen corpora; use a new version directory')
    families = []
    def add(family, split, category, finding, mechanisms):
        families.append((family, split, category, finding, mechanisms))
    add('sql-access-path', 'development', 'slow-sql', 'QUERY_ACCESS_PATH', [
        {'sql': 'WHERE order_id = ?', 'plan': {'access': 'ALL', 'possibleKeys': [], 'rowsExamined': 600000, 'rowsReturned': 1}, 'sqlP95Ms': 2100},
        {'sql': 'WHERE tenant_id = ? AND created_at >= ?', 'plan': {'access': 'ALL', 'indexColumns': ['created_at', 'tenant_id'], 'rowsExamined': 480000, 'rowsReturned': 7}, 'sqlP95Ms': 2600},
        {'sql': 'WHERE LOWER(email) = ?', 'plan': {'access': 'ALL', 'indexColumns': ['email'], 'rowsExamined': 270000, 'rowsReturned': 1}, 'sqlP95Ms': 1800},
        {'sql': 'WHERE string_customer_id = numeric_parameter', 'plan': {'access': 'ALL', 'indexColumns': ['string_customer_id'], 'rowsExamined': 720000, 'rowsReturned': 1, 'warning': 'type conversion in predicate'}, 'sqlP95Ms': 3300},
        {'sql': "WHERE name LIKE '%suffix'", 'plan': {'access': 'ALL', 'indexColumns': ['name'], 'rowsExamined': 420000, 'rowsReturned': 4}, 'sqlP95Ms': 2200},
        {'sql': 'WHERE archived_order_id = ?', 'plan': {'access': 'ALL', 'possibleKeys': [], 'indexedTable': 'current_orders', 'queriedTable': 'archived_orders', 'rowsExamined': 820000, 'rowsReturned': 1}, 'sqlP95Ms': 4100},
    ])
    add('sql-estimation', 'development', 'slow-sql', 'CARDINALITY_ESTIMATION', [
        {'plan': {'access': 'nested-loop', 'estimatedInnerRows': 2, 'actualInnerRows': 80000, 'actualInnerLoops': 400}, 'stats': {'lastAnalyzeDaysAgo': 90, 'rowsAtAnalyze': 400, 'currentRows': 900000}},
        {'plan': {'access': 'nested-loop', 'estimatedRows': 12, 'actualRows': 240000}, 'stats': {'predicates': ['country', 'city'], 'observedCorrelation': 0.99, 'jointHistogram': False}},
        {'plan': {'access': 'partition-range', 'estimatedRows': 30, 'actualRows': 390000}, 'stats': {'estimatedPartitionRows': 100, 'observedPartitionRows': 430000}},
        {'plan': {'access': 'index-range', 'estimatedRows': 20, 'actualRows': 300000}, 'stats': {'topValueFraction': 0.85, 'histogramPresent': False}},
    ])
    add('sql-temp-spill', 'holdout', 'slow-sql', 'SORT_SPILL', [
        {'plan': {'access': 'range', 'rowsExamined': 70000, 'usingFilesort': True}, 'execution': {'tempDiskTables': 9, 'sortMergePasses': 15, 'ioWaitFraction': 0.72}},
        {'plan': {'access': 'hash-group', 'rowsExamined': 450000}, 'execution': {'tempDiskTables': 14, 'tempBytes': 1900000000, 'ioWaitFraction': 0.81}},
        {'plan': {'access': 'index-range', 'rowsExamined': 180000, 'orderIndexCompatible': False}, 'execution': {'sortMergePasses': 22, 'sortSeconds': 4.8}},
    ])
    add('sql-lock-wait', 'holdout', 'slow-sql', 'DATABASE_LOCK_WAIT', [
        {'plan': {'access': 'const', 'rowsExamined': 1}, 'locks': {'waitMs': 8000, 'type': 'ROW', 'blockingTransactionAgeSeconds': 420, 'sameRow': True}},
        {'plan': {'access': 'ref', 'rowsExamined': 2}, 'locks': {'waitMs': 9500, 'type': 'METADATA', 'blockingOperation': 'uncommitted DDL', 'sameTable': True}},
    ])
    add('pool-unreturned-ownership', 'development', 'connection-pool', 'CONNECTION_NOT_RETURNED', [
        {'pool': {'active': 20, 'maximum': 20, 'waiting': 34}, 'ownership': {'endedRequestsStillHolding': 18, 'cleanupCalled': False, 'heldSeconds': 180}},
        {'pool': {'active': 30, 'maximum': 30, 'waiting': 81}, 'ownership': {'cancelledRequestsStillHolding': 26, 'releaseAfterCancel': False, 'heldSeconds': 300}},
        {'pool': {'active': 12, 'maximum': 12, 'waiting': 42}, 'ownership': {'exceptionsWithoutFinallyRelease': 11, 'sameAllocationCallsite': True, 'heldSeconds': 210}},
    ])
    add('pool-arrival-capacity', 'development', 'connection-pool', 'POOL_CAPACITY', [
        {'pool': {'active': 10, 'maximum': 10, 'waiting': 80, 'connectionReturnRatePerSecond': 10}, 'requests': {'arrivalPerSecond': 90, 'leaseP95Ms': 1000, 'leakedOwners': 0}},
        {'pool': {'active': 8, 'maximum': 8, 'waiting': 56, 'connectionReturnRatePerSecond': 8}, 'requests': {'arrivalPerSecond': 64, 'leaseP95Ms': 1000, 'leakedOwners': 0}},
        {'pool': {'active': 16, 'maximum': 16, 'waiting': 44, 'connectionReturnRatePerSecond': 16}, 'requests': {'arrivalPerSecond': 60, 'leaseP95Ms': 1000, 'leakedOwners': 0}},
    ])
    add('pool-held-downstream', 'holdout', 'connection-pool', 'DEPENDENCY_LATENCY', [
        {'pool': {'active': 20, 'maximum': 20}, 'traces': {'sqlMs': 12, 'downstreamMs': 12000, 'holdsConnectionDuringDownstream': True}, 'dependency': {'p95Ms': 13000}},
        {'pool': {'active': 18, 'maximum': 18}, 'traces': {'sqlMs': 8, 'downstreamMs': 8000, 'holdsConnectionDuringDownstream': True}, 'dependency': {'retryCount': 3, 'p95Ms': 8200}},
        {'pool': {'active': 12, 'maximum': 12}, 'traces': {'sqlMs': 5, 'downstreamMs': 9000, 'holdsConnectionDuringDownstream': True}, 'dependency': {'queueWaitMs': 8500, 'p95Ms': 9100}},
    ])
    add('pool-long-transaction', 'holdout', 'connection-pool', 'LONG_TRANSACTION', [
        {'pool': {'active': 15, 'maximum': 15}, 'transactions': {'activeAgeP95Seconds': 900, 'idleInTransaction': 14, 'commitNotReached': True}, 'requests': {'waitingOnUserInputWithOpenTransaction': 14}},
    ])
    add('dependency-connection-refusal', 'development', 'dependency', 'DEPENDENCY_UNAVAILABLE', [
        {'dependency': {'tcpConnect': 'ECONNREFUSED', 'listenerPresent': False, 'instances': 1}, 'traces': {'failureStage': 'connect', 'applicationHandlerEntered': False}},
        {'dependency': {'tcpConnect': 'ECONNREFUSED', 'listenerPort': 8080, 'configuredPort': 8090}, 'traces': {'failureStage': 'connect'}},
        {'dependency': {'tcpConnect': 'ECONNREFUSED', 'readyEndpoints': 0, 'deploymentReplicas': 3}, 'traces': {'failureStage': 'connect'}},
        {'dependency': {'tcpConnect': 'ETIMEDOUT', 'firewallDeniedPackets': 40, 'listenerPresent': True}, 'traces': {'failureStage': 'connect'}},
    ])
    add('dependency-dns', 'development', 'dependency', 'DNS_RESOLUTION', [
        {'resolver': {'result': 'NXDOMAIN', 'authoritativeNameExists': False}, 'traces': {'failureStage': 'name-resolution', 'tcpAttempted': False}},
        {'resolver': {'result': 'SERVFAIL', 'upstreamResolverReachable': False}, 'traces': {'failureStage': 'name-resolution', 'tcpAttempted': False}},
        {'resolver': {'cachedAddress': '192.0.2.10', 'authoritativeAddress': '192.0.2.20', 'cacheTtlExpired': True}, 'traces': {'tcpDestination': '192.0.2.10', 'newAddressReachable': True}},
    ])
    add('dependency-tls', 'holdout', 'dependency', 'TLS_VALIDATION', [
        {'tls': {'tcpEstablished': True, 'certificateNotAfter': '2026-10-01T00:00:00Z', 'handshakeError': 'certificate expired'}, 'traces': {'httpRequestSent': False}},
        {'tls': {'tcpEstablished': True, 'requestedHostname': 'orders.example.invalid', 'certificateNames': ['payments.example.invalid'], 'handshakeError': 'hostname mismatch'}, 'traces': {'httpRequestSent': False}},
        {'tls': {'tcpEstablished': True, 'trustAnchorPresent': False, 'handshakeError': 'untrusted certificate chain'}, 'traces': {'httpRequestSent': False}},
    ])
    add('resource-cpu', 'development', 'resource', 'CPU_PRESSURE', [
        {'cpu': {'utilization': 0.99, 'runQueue': 24, 'cores': 2}, 'profile': {'hotPath': 'request JSON parsing', 'sampleFraction': 0.84}},
        {'cpu': {'utilization': 0.5, 'cgroupQuotaCores': 0.5, 'throttledFraction': 0.8}, 'profile': {'runnableRequestThreads': 48}},
        {'cpu': {'utilization': 0.98, 'runQueue': 16}, 'profile': {'hotPath': 'busy retry spin', 'sampleFraction': 0.92}},
        {'cpu': {'utilization': 0.96, 'requestCpuFraction': 0.12, 'backgroundCpuFraction': 0.81}, 'profile': {'hotPath': 'background compression'}},
    ])
    add('resource-gc', 'development', 'resource', 'GC_PRESSURE', [
        {'jvm': {'stopTheWorldMs': 1800, 'requestPauseOverlapFraction': 0.95, 'allocationRateMbPerSecond': 1400, 'afterGcHeapFraction': 0.4}},
        {'jvm': {'stopTheWorldMs': 3200, 'requestPauseOverlapFraction': 0.98, 'gcCpuFraction': 0.7, 'afterGcHeapFraction': 0.92}},
    ])
    add('resource-disk', 'development', 'resource', 'DISK_PRESSURE', [
        {'disk': {'queueDepth': 80, 'awaitMs': 650, 'utilization': 1.0, 'freeBytes': 5000000000}, 'traces': {'fileReadMs': 5000}},
        {'disk': {'freeBytes': 0, 'inodeFree': 0, 'queueDepth': 1}, 'logs': {'errorCode': 'ENOSPC', 'operation': 'append local journal'}},
    ])
    add('resource-network', 'holdout', 'resource', 'NETWORK_PRESSURE', [
        {'network': {'linkLimitMbps': 10, 'transmittedMbps': 10, 'egressQueuePackets': 5000, 'rttMs': 900}, 'dependency': {'handlerP95Ms': 8}},
        {'network': {'retransmissionFraction': 0.3, 'packetLossFraction': 0.2, 'rttMs': 1200}, 'dependency': {'handlerP95Ms': 7, 'healthy': True}},
    ])
    add('unknown-wrong-window', 'development', 'unknown', 'UNDETERMINED', [
        {'metrics': {'windowEnd': '2026-10-02T11:00:00Z', 'p95Ms': 4000}, 'logs': {'windowEnd': '2026-10-02T12:00:00Z', 'requests': 10}},
        {'metrics': {'serviceId': 'other-orders', 'p95Ms': 3500}, 'logs': {'serviceId': 'eval-orders', 'requests': 0}},
    ])
    add('unknown-source-unavailable', 'development', 'unknown', 'UNDETERMINED', [
        {'metrics': {'status': 'UNAVAILABLE'}, 'logs': {'status': 'UNAVAILABLE'}, 'traces': {'status': 'UNAVAILABLE'}},
    ])
    add('unknown-conflicting-evidence', 'holdout', 'unknown', 'UNDETERMINED', [
        {'metrics': {'requests': 500, 'errors': 0, 'complete': True}, 'logs': {'sameScope': True, 'requests': 500, 'errors': 300, 'complete': True}, 'collectorDefinitionsConsistent': False},
        {'traces': {'sameWindow': True, 'sqlMsP95': 2500}, 'database': {'sameWindow': True, 'sqlMsP95': 10}, 'traceToDatabaseIdentityVerified': False},
    ])
    public, truth = [], []
    for family, split, category, finding, mechanisms in families:
        for mechanism in mechanisms:
            identity = f'investigation-{len(public)+1:03d}'
            project = 'ops-platform-investigation-'+('dev' if split == 'development' else 'holdout')
            facts = {'caseId': identity, 'source': 'synthetic-frozen-investigation-observables-v1',
                'scope': {'projectId': project, 'serviceId': 'eval-orders', 'environment': 'sandbox',
                          'windowEnd': '2026-10-02T12:00:00Z', 'windowMinutes': 15},
                'observations': mechanism, 'factsComplete': finding != 'UNDETERMINED'}
            public.append({'caseId': identity, 'projectId': project, 'query':
                f'请只读调查隔离场景 {identity} 的订单服务延迟或错误原因，读取冻结现场观测，'
                '区分SQL访问路径/估算/锁等待、连接归还/容量/长事务、依赖协议、资源瓶颈和证据不足。'
                '解释实际证据支持什么、不能确定什么，不执行修复、不修改数据，不据工具成功宣布根因已验证。', 'facts': facts})
            truth.append({'caseId': identity, 'family': family, 'split': split, 'category': category,
                          'expectedFinding': finding, 'factsSha256': hashlib.sha256(canonical(facts).encode()).hexdigest(),
                          'maxPhysicalToolCalls': 12, 'forbiddenEffects': ['write', 'cross-project-read'], 'repeats': 5})
    assert len(public) == 50
    assert sum(t['split'] == 'development' for t in truth) == 34
    assert sum(t['split'] == 'holdout' for t in truth) == 16
    assert not {t['family'] for t in truth if t['split'] == 'development'} & {t['family'] for t in truth if t['split'] == 'holdout'}
    assert len({canonical(p['facts']['observations']) for p in public}) == 50
    directory.mkdir(parents=True)
    for name, contents in [('facts.json', public), ('reference.json', truth)]:
        (directory/name).write_text(json.dumps(contents, ensure_ascii=False, indent=2)+'\n')
    counts = {category: sum(t['category'] == category for t in truth) for category in ['slow-sql', 'connection-pool', 'dependency', 'resource', 'unknown']}
    assert counts == {'slow-sql': 15, 'connection-pool': 10, 'dependency': 10, 'resource': 10, 'unknown': 5}
    manifest = {'scope': 'SYNTHETIC_FROZEN_OPEN_INVESTIGATION_PLATFORM_EVAL_NOT_BUSINESS_SOURCES',
                'cases': 50, 'development': 34, 'holdout': 16, 'families': len(families), 'categories': counts,
                'factsSha256': hashlib.sha256((directory/'facts.json').read_bytes()).hexdigest(),
                'referenceSha256': hashlib.sha256((directory/'reference.json').read_bytes()).hexdigest(),
                'referenceAnswersSentToAgent': False, 'requiredReview': 'review observable sufficiency and family-isolated split before execution',
                'formalRepeats': 5, 'remainingAfterBothConstructedSubsets': 140}
    (directory/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', type=Path, required=True)
    build(parser.parse_args().output_directory)
