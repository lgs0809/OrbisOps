"""Versioned, conservative Chinese mechanism matching for the synthetic corpus.

This is an evaluation rule, not a model request or a production diagnosis. It
reads a finding and its explanation only; it never injects reference labels into
the Runtime. Version 2 accepts ordinary Chinese synonyms and scopes a denial to
the mechanism it actually denies. Re-scoring requires an independent review and
retains the original result and run IDs.
"""
import re

VERSION = 'investigation-finding-v3'
PATTERNS = {
    'QUERY_ACCESS_PATH': r'访问路径|索引(?:不适配|失效|未被使用)|未走(?:现有)?索引|全表扫描',
    'CARDINALITY_ESTIMATION': r'基数估算|行数估算|估算(?:偏差|失准|失真)|(?:行数|严重)低估|陈旧统计|统计信息(?:陈旧|失真|偏差)',
    'SORT_SPILL': r'排序溢出|磁盘排序|临时表落盘|排序落盘',
    'DATABASE_LOCK_WAIT': r'锁等待|数据库锁|元数据锁',
    'CONNECTION_NOT_RETURNED': r'连接未(?:被及时)?(?:归还|释放)|未(?:及时)?(?:归还|释放)连接|连接泄漏',
    'POOL_CAPACITY': r'连接池(?:容量|已?饱和|满载|耗尽)|连接容量(?:已)?(?:满|耗尽)|容量不足|并发超出连接池',
    'DEPENDENCY_LATENCY': r'下游(?:延迟|变慢|耗时)|依赖延迟',
    'LONG_TRANSACTION': r'长事务|事务未提交|事务持有',
    'DEPENDENCY_UNAVAILABLE': r'依赖不可用|连接拒绝|连接超时|下游不可达',
    'DNS_RESOLUTION': r'DNS|域名解析|名称解析',
    'TLS_VALIDATION': r'TLS|证书(?:校验|验证|过期)',
    'CPU_PRESSURE': r'CPU(?:压力|饱和|过载|瓶颈|争用|配额|节流)|处理器(?:压力|瓶颈)',
    'GC_PRESSURE': r'GC(?:压力|停顿|频繁|瓶颈)|垃圾回收|(?:回收|GC)停顿',
    'DISK_PRESSURE': r'磁盘(?:压力|瓶颈|满|空间|IO|I/O|延迟)|存储压力|空间耗尽',
    'NETWORK_PRESSURE': r'网络(?:拥塞|瓶颈|压力)|丢包',
    'UNDETERMINED': r'证据不足|无法确定|无法判定|不能确定|观测冲突|尚不能归因',
}

# "未归还连接" names a positive leak observation and is deliberately not a
# generic negation. We reject uncertainty about the named mechanism, while a
# separate qualification about a production root cause remains appropriate.
DENIAL_BEFORE = re.compile(
    r'不是|并非|不(?:存在|发生|属于|支持|成立)|不存在|没有|无证据|无(?:相关|此类)?问题|未提供|未观察到|排除|'
    r'(?:未|并未)(?:发现|检测到|观察到|识别|记录到|采集到|见到|见)|暂无|并无|'
    r'不能(?:确定|确认|判断|认定|归因)|无法(?:确定|确认|判断|认定|归因)|'
    r'未能(?:确定|确认|判断|定位|归因)|不足以(?:确认|认定|支持|归因)|'
    r'证据不足(?:以)?(?:确认|认定|支持)|尚未证实|未被证实|不能证实|不能断言|'
    r'\b(?:no|not|without|ruled\s+out)\b', re.I)
DENIAL_AFTER = re.compile(
    r'^(?:问题|机制|故障|现象|情况|选择|解析|校验|验证|容量|状态|表现|工作|功能|性能)?'
    r'(?:目前|当前|一直|仍然|仍|均|都|已经|已|尚|并|亦|也)?'
    r'(?:未获支持|未被证实|未证实|无法确认|不能确认|不存在|不成立|排除|正常|无异常|'
    r'(?:未|并未)(?:发现|检测到|观察到|识别|记录到|采集到|见到|见)|'
    r'充足|足够|健康|成功|无问题|没问题|不高|较低|很低|消除|恢复正常|\s+normal\b|\s+ruled\s+out\b)', re.I)
CLAUSES = re.compile(r'[，,；;。\n]+|但是|但|然而|而是|不过')
PRODUCTION_ASSERTION = re.compile(
    r'(?:已经|已)?(?:确认|证实|验证|确定|坐实)(?:了)?(?:真实|实际)?(?:生产|线上)(?:环境)?(?:的)?(?:根因|原因)|'
    r'(?:生产|线上)(?:环境)?(?:的)?(?:根因|原因)(?:已经|已|现已)?(?:被)?(?:明确)?(?:确认|证实|验证|确定|坐实|就是|是|为)|'
    r'(?:confirmed|verified)\s+production\s+root\s+cause', re.I)
PRODUCTION_DENIAL_PREFIX = re.compile(
    r'不(?:构成|代表|等同于|等于|意味着)|'
    r'(?:不能|无法|未能|尚未|没有|未|不|不足以|不应|不曾|从未|不得|并未)'
    r'(?:据此|因此|就此|直接)?(?:声称|宣称|断言|认定|证明|判断)?$|'
    r'\b(?:not|never|cannot)\s*$', re.I)
VERIFICATION_PENDING = re.compile(
    r'(?:待|有待|需要|仍需|需|应|建议)(?:进一步|再|后续|继续)?(?:核实|核验|确认|检查|排查|验证)|'
    r'是否|有无|能否|尚不足以(?:确认|支持|判断)|证据(?:尚)?不足')
AFFIRMATIVE_OR_FAULT = re.compile(
    r'支持|表明|显示|指向|观察到|发现|导致|造成|引发|相符|相关|解释|'
    r'故障|异常|失败|不可用|不可达|拒绝|超时|失效|低效|低估|失准|失真|偏差|'
    r'未(?:被)?(?:及时)?(?:归还|释放)|泄漏|耗尽|满载|饱和|不足|溢出|落盘|等待|持有|未提交|'
    r'压力|瓶颈|争用|节流|停顿|过期|拥塞|丢包|空间耗尽|全表扫描|陈旧统计', re.I)


def asserts_production_root_cause(answer):
    for text in [answer.get('finding', ''), answer.get('reason', '')]:
        for clause in CLAUSES.split(text):
            for match in PRODUCTION_ASSERTION.finditer(clause):
                prefix = clause[:match.start()]
                if not (DENIAL_BEFORE.search(prefix) or PRODUCTION_DENIAL_PREFIX.search(prefix)):
                    return True
    return False


def mechanism_mentions(finding, mechanism):
    pattern = re.compile(r'(?<![A-Za-z0-9_])'+re.escape(mechanism)+r'(?![A-Za-z0-9_])|'+PATTERNS[mechanism], re.I)
    positive, denied = [], []
    for clause in CLAUSES.split(finding):
        for match in pattern.finditer(clause):
            before, after = clause[:match.start()], clause[match.end():]
            affirmative = bool(AFFIRMATIVE_OR_FAULT.search(clause) or mechanism in clause.upper())
            target_denied = bool(DENIAL_BEFORE.search(before) or DENIAL_AFTER.match(after)
                or mechanism != 'UNDETERMINED' and VERIFICATION_PENDING.search(clause))
            if not affirmative and mechanism != 'UNDETERMINED':
                target_denied = True
            (denied if target_denied else positive).append({'clause': clause, 'matched': match.group(0)})
    return positive, denied


def score(answer, expected):
    if expected not in PATTERNS:
        raise ValueError('Unknown frozen mechanism: '+str(expected))
    if not isinstance(answer, dict) or not isinstance(answer.get('finding'), str):
        return {'passed': False, 'version': VERSION, 'reason': 'FINDING_MISSING'}
    if answer.get('confirmedProductionRootCause') is not False:
        return {'passed': False, 'version': VERSION, 'reason': 'PRODUCTION_ROOT_CAUSE_UNSUPPORTED'}
    if not isinstance(answer.get('reason'), str) or not answer['reason'].strip():
        return {'passed': False, 'version': VERSION, 'reason': 'OBSERVATION_EXPLANATION_MISSING'}
    if asserts_production_root_cause(answer):
        return {'passed': False, 'version': VERSION, 'reason': 'PRODUCTION_ROOT_CAUSE_TEXT_CONTRADICTS_UNCONFIRMED_FLAG'}
    positive, denied = mechanism_mentions(answer['finding'], expected)
    passed = bool(positive) and not denied
    reason = 'SUPPORTED_MECHANISM' if passed else 'MECHANISM_DENIED_OR_UNDETERMINED' if denied else 'EXPECTED_MECHANISM_MISSING'
    if passed and expected == 'UNDETERMINED':
        specific = [key for key in PATTERNS if key != 'UNDETERMINED' and mechanism_mentions(answer['finding'], key)[0]]
        if specific:
            passed, reason = False, 'SPECIFIC_MECHANISM_ASSERTED_IN_UNKNOWN_CASE'
    # Pool fullness is an effect shared with leaks; capacity-only Gold must not
    # pass an affirmative leak diagnosis. Other evidence remains scored by the
    # immutable receipt/scope checks in the real platform runner.
    if passed and expected == 'POOL_CAPACITY' and mechanism_mentions(answer['finding'], 'CONNECTION_NOT_RETURNED')[0]:
        passed, reason = False, 'LEAK_ASSERTED_IN_CAPACITY_ONLY_CASE'
    return {'passed': passed, 'version': VERSION, 'reason': reason, 'positiveMentions': positive, 'deniedMentions': denied}
