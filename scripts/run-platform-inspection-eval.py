#!/usr/bin/env python3
"""Real deployed Runtime/Harness + configured Terra + frozen read-only MCP inspection evaluation.

Each attempt has a new chat/run, normal natural-language input, actual model/tool
receipts and independent peer hashes. It never calls task acceptance, Skill
publication or memory-write APIs. Development screening and 5-repeat evaluation
are recorded separately. Each synthetic layer retains its own declared task scope;
these runs alone do not constitute the complete 240-task suite.
"""
import argparse
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import runpy
import secrets
import signal
import subprocess
import time
import uuid
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
DATASET = 'inspection'
CORPUS = ROOT/'deploy/.acceptance-private/platform-inspection-v1'
SEED = runpy.run_path(str(ROOT/'scripts/seed-local-acceptance.py'))
API = SEED['request']
CONFIGURATION_RAW_API = API
CREDENTIALS = json.loads((ROOT/'deploy/.acceptance-private/admin.json').read_text())
TOKEN = SEED['login'](CREDENTIALS)
STATEMENTS = []
SCORING = None
SCORING_REVIEW = None
STRUCTURE_REVIEW = None
NATIVE_TRUTH = []
INSPECTION_REVIEW = None
TEST_SUMMARY = None
DEPLOYMENT_EVIDENCE = None
PRIOR_RESULTS = []
STOP_REQUESTED_AT = None


def request_admission_stop(_signal, _frame):
    """Stop admitting cases after the current native run, without cancelling it."""
    global STOP_REQUESTED_AT
    if STOP_REQUESTED_AT is None:
        STOP_REQUESTED_AT = dt.datetime.now(dt.timezone.utc).isoformat()

PEERS = {'inspection': (8581, 'platform-eval-mcp'),
         'investigation': (8681, 'platform-investigation-mcp'),
         'skill': (8981, 'platform-skill-mcp')}
FINDING_NAMES = {
    'QUERY_ACCESS_PATH': ['访问路径', '索引不适配', '索引失效', '全表扫描'],
    'CARDINALITY_ESTIMATION': ['估算偏差', '基数估算', '统计信息'],
    'SORT_SPILL': ['排序溢出', '磁盘排序', '临时表落盘', '排序落盘'],
    'DATABASE_LOCK_WAIT': ['锁等待', '数据库锁', '元数据锁'],
    'CONNECTION_NOT_RETURNED': ['连接未归还', '连接泄漏', '连接未释放'],
    'POOL_CAPACITY': ['连接池容量', '容量不足', '并发超出连接池', '连接池饱和'],
    'DEPENDENCY_LATENCY': ['下游延迟', '依赖延迟', '下游变慢', '下游耗时'],
    'LONG_TRANSACTION': ['长事务', '事务未提交', '事务持有'],
    'DEPENDENCY_UNAVAILABLE': ['依赖不可用', '连接拒绝', '连接超时', '下游不可达'],
    'DNS_RESOLUTION': ['DNS', '域名解析', '名称解析'],
    'TLS_VALIDATION': ['TLS', '证书校验', '证书验证', '证书过期'],
    'CPU_PRESSURE': ['CPU', '处理器压力', '处理器瓶颈'],
    'GC_PRESSURE': ['GC', '垃圾回收', '停顿'],
    'DISK_PRESSURE': ['磁盘', '存储压力', '空间耗尽'],
    'NETWORK_PRESSURE': ['网络拥塞', '网络瓶颈', '网络压力', '丢包'],
    'UNDETERMINED': ['证据不足', '无法确定', '无法判定', '不能确定', '观测冲突'],
}


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def configuration_api(path, *args, **kwargs):
    """Retry only the explicit pre-controller rate rejection, never unknown writes."""
    for attempt in range(8):
        time.sleep(1.1)
        try: return CONFIGURATION_RAW_API(path, *args, **kwargs)
        except RuntimeError as failure:
            if 'HTTP 429, code=0006' not in str(failure) or attempt == 7: raise
            delay = min(16, 2**attempt)
            print(json.dumps({'stage': 'RATE_REJECTION_RETRY', 'path': path, 'delaySeconds': delay}), flush=True)
            time.sleep(delay)


def normalized_graph_parts(definition):
    value = json.loads(json.dumps(definition))
    for node in value['nodes']:
        for key in ['mcpServers', 'mcpIds', 'executionTargetIds', 'skills']: node.setdefault(key, [])
        if node.get('type') == 'AGENT': node['config'].update(mode=node['mode'].lower(), role='general')
        if node.get('type') == 'ROUTER': node.setdefault('outputKey', 'selectedRoutes')
    for edge in value['edges']: edge.setdefault('dataMapping', {})
    def clean(item):
        if isinstance(item, dict): return {key: clean(child) for key, child in item.items() if child is not None}
        if isinstance(item, list): return [clean(child) for child in item]
        return item
    return clean(value['nodes']), clean(value['edges'])


def load_investigation_scoring(review_path):
    if not review_path: raise RuntimeError('Independent scoring review is required for investigation execution')
    review = json.loads(review_path.read_text())
    source = ROOT/'scripts/platform-investigation-scoring.py'
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if review.get('status') != 'APPROVED_STATIC_SCORER' or review.get('sourceSha256') != digest:
        raise RuntimeError('Scorer changed or independent review not approved')
    spec = importlib.util.spec_from_file_location('investigation_scoring', source)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module, {'version': module.VERSION, 'sourceSha256': digest,
        'independentReviewPath': str(review_path), 'independentReviewSha256': hashlib.sha256(review_path.read_bytes()).hexdigest(),
        'boundary': review.get('boundary')}


def runtime_identity():
    paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    source_files = {path: hashlib.sha256((ROOT/path).read_bytes()).hexdigest() for path in sorted(set(paths))
        if path and (ROOT/path).is_file() and path.startswith(('server/', 'scripts/', 'web/src/', 'deploy/'))
        and Path(path).suffix in ('.java', '.py', '.xml', '.sql', '.yml', '.yaml', '.ts', '.tsx', '.vue', '.css')}
    return {'deployedJarSha256': subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
            'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0],
        'gitHead': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'sourceTreeSha256': hashlib.sha256(canonical(source_files).encode()).hexdigest(),
        'sourceFiles': source_files,
        'runnerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}


def q(value):
    return "'"+str(value).replace('\\', '\\\\').replace("'", "''")+"'"


def sql(query):
    STATEMENTS.append(query.rstrip(';')+';')
    result = subprocess.run(['docker', 'exec', '-i', 'orbisops-acceptance-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw orbisops_acceptance'],
        input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def rows(query):
    value = sql(query)
    return [json.loads(line) for line in value.splitlines()] if value else []


def verify_corpus():
    manifest = json.loads((CORPUS/'manifest.json').read_text())
    for name, key in [('facts.json', 'factsSha256'), ('reference.json', 'referenceSha256')]:
        if hashlib.sha256((CORPUS/name).read_bytes()).hexdigest() != manifest[key]:
            raise RuntimeError('Frozen corpus changed; new corpus/review required')
    return manifest


def verify_skill_truth(case_ids):
    if DATASET != 'skill': return None
    manifest = verify_corpus()
    if STRUCTURE_REVIEW is None: raise RuntimeError('Independent structural corpus review required')
    review = json.loads(STRUCTURE_REVIEW.read_text())
    if review.get('status') != 'APPROVED_SUPPLEMENTAL_STRUCTURE_CORPUS_AWAITING_NATIVE_TRUTH' or any(
            review.get(key) != manifest[key] for key in ('factsSha256', 'referenceSha256')):
        raise RuntimeError('Exact approved structural corpus required')
    jar = runtime_identity()['deployedJarSha256']
    source = ROOT/'scripts/acceptance/SkillGovernanceTruthProbe.java'
    valid = set()
    reports = []
    for path in NATIVE_TRUTH:
        truth = json.loads(path.read_text())
        if truth.get('status') != 'PASS' or truth.get('exitCode') != 0 or truth.get('jarSha256') != jar or any(
                truth.get(key) != manifest[key] for key in ('factsSha256', 'referenceSha256')) or \
                truth.get('probeSourceSha256') != hashlib.sha256(source.read_bytes()).hexdigest():
            raise RuntimeError('Actual native truth report changed or does not match the deployed JAR')
        valid.update(row['caseId'] for row in truth['results'] if row['status'] == 'PASS')
        reports.append({'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    if not set(case_ids) <= valid: raise RuntimeError('Every selected model case requires actual packaged native truth first')
    return {'reviewPath': str(STRUCTURE_REVIEW), 'reviewSha256': hashlib.sha256(STRUCTURE_REVIEW.read_bytes()).hexdigest(),
        'nativeTruthReports': reports, 'boundary': review['boundary']}


def peer_evidence():
    # The peer has no host port. Only the named namespace-local container is read.
    manifest = verify_corpus()
    port = manifest.get('mcpPeerPort', PEERS[DATASET][0])
    container = manifest.get('mcpPeerService', PEERS[DATASET][1])
    code = ('import json,os,urllib.request; r=urllib.request.Request("http://127.0.0.1:'+str(port)+'/evidence",'
            'headers={"Authorization":"Bearer "+os.environ["MCP_ACCEPTANCE_TOKEN"]}); '
            'print(urllib.request.urlopen(r,timeout=5).read().decode())')
    return json.loads(subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-'+container+'-1', 'python', '-c', code], text=True))


def mount_isolation():
    manifest = verify_corpus()
    container = manifest.get('mcpPeerService', PEERS[DATASET][1])
    value = json.loads(subprocess.check_output(['docker', 'inspect', 'orbisops-acceptance-'+container+'-1'], text=True))[0]
    mounts = [{k: m.get(k) for k in ['Type', 'Source', 'Destination', 'RW', 'Name']} for m in value['Mounts']]
    binds = {m['Destination']: m for m in mounts if m['Type'] == 'bind'}
    # Docker Desktop may expose the same macOS bind with its explicit VM
    # /host_mnt prefix. Verify both the exact host path and mounted file hash.
    facts_source = binds.get('/eval/facts.json', {}).get('Source', '')
    canonical_source = facts_source.removeprefix('/host_mnt') if facts_source.startswith('/host_mnt/') else facts_source
    mounted_digest = subprocess.check_output(['docker','exec','orbisops-acceptance-'+container+'-1',
        'python','-c', 'import hashlib;print(hashlib.sha256(open("/eval/facts.json","rb").read()).hexdigest())'],text=True).strip()
    checks = {'factsOnly': set(binds) == {'/eval/facts.json', '/eval/server.py'},
        'factsCorrect': canonical_source == str(CORPUS/'facts.json') and mounted_digest == manifest['factsSha256'],
        'bindReadOnly': all(not m['RW'] for m in binds.values()),
        'dedicatedLedger': len([m for m in mounts if m['Type'] == 'volume' and m['Destination'] == '/state']) == 1,
        'readOnlyRoot': value['HostConfig']['ReadonlyRootfs'], 'noHostPorts': not value['HostConfig'].get('PortBindings')}
    if not all(checks.values()): raise RuntimeError('Peer facts/reference isolation failed')
    return {'checks': checks, 'mounts': mounts,
        'mountedFactsSha256':mounted_digest,
        'peerSourceSha256': hashlib.sha256((ROOT/'scripts/fixtures/platform-eval-mcp.py').read_bytes()).hexdigest()}


def isolation(project):
    actor = 'ops_eval_'+DATASET+'_'+project.rsplit('-', 1)[-1]
    value = rows('SELECT JSON_OBJECT('
        "'accepted',(SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE project_id="+q(project)+" AND outcome='SUCCEEDED'),"
        "'skillJobs',(SELECT COUNT(*) FROM ai_ops_skill_evolution_job WHERE project_id="+q(project)+"),"
        "'skillSources',(SELECT COUNT(*) FROM ai_ops_skill_verified_contribution WHERE project_id="+q(project)+"),"
        "'skills',(SELECT COUNT(*) FROM ai_ops_skill WHERE project_id="+q(project)+"),"
        "'explicitMemory',(SELECT COUNT(*) FROM ai_ops_memory WHERE project_id="+q(project)+" OR user_id="+q(actor)+"),"
        "'extractedMemory',(SELECT COUNT(*) FROM ai_ops_memory_item WHERE user_id="+q(actor)+"));")[0]
    if any(value.values()):
        raise RuntimeError('Evaluation learning isolation violated: '+json.dumps(value))
    return value


def definition(project, mcp):
    identity = 'ops-platform-'+DATASET+'-'+project.rsplit('-', 1)[-1]
    if verify_corpus().get('configurationRevision'): identity += '-'+CORPUS.name.rsplit('-', 1)[-1]
    value = {'agentId': identity, 'schemaVersion': 1, 'projectId': project,
        'name': '冻结巡检评测 · '+project.rsplit('-', 1)[-1], 'engine': 'HYBRID',
        'definitionKind': 'SPECIALIZED_WORKFLOW', 'workflowInvocationMode': 'MANUAL_ONLY',
        'workflowAutoSelectEnabled': False, 'queryRewriteEnabled': False, 'ragEnabled': False,
        'description': '平台合成评测；只有只读冻结观测MCP，无参考答案，无学习/记忆/任务验收工具。',
        'instruction': '按用户给出的巡检标准取证判断；合成评测不提交任务成功或沉淀Skill。',
        'startNodeId': 'start',
        'nodes': [{'nodeId': 'start', 'type': 'START', 'config': {'inputKeys': ['query'], 'maxRealToolCalls': 12}},
            {'nodeId': 'inspect', 'type': 'AGENT', 'mode': 'REACT', 'agent': 'ops-platform-eval-reader',
             'modelId': 'ops-acceptance-terra', 'mcpIds': [mcp], 'outputKey': 'inspection',
             'instruction': '你执行隔离冻结巡检。先加载 inspection_snapshot 完整Schema，再读取用户指定场景的实际观测。'
                '只读取当前可信项目的场景。不要把题名、命名或工具目录当结论；不得读取参考答案。'
                '按用户所给标准检查对象、时间窗、完整性、可达性、请求数、错误率和p95。'
                '仅调用这一个只读MCP；不得调用Task验收、Skill、长期记忆或写入工具。'
                '模型内部输出一个JSON对象，字段 conclusion 使用用户要求的中文：健康、异常、证据不足；'
                'caseId 必须为本次实际场景，reason 写自然语言依据。用户无需输入JSON。',
             'config': {'inheritProjectCapabilities': False, 'outputContract': {'format': 'JSON', 'schema': {
                 'type': 'object', 'required': ['conclusion', 'caseId', 'reason'], 'properties': {
                     'conclusion': {'enum': ['健康', '异常', '证据不足', 'HEALTHY', 'UNHEALTHY', 'INSUFFICIENT_DATA']},
                     'caseId': {'type': 'string'}, 'reason': {'type': 'string'}}}}}},
            {'nodeId': 'end', 'type': 'END', 'config': {'outputKeys': ['inspection']}}],
        'edges': [{'from': 'start', 'to': 'inspect', 'conditionType': 'always', 'condition': 'always'},
                  {'from': 'inspect', 'to': 'end', 'conditionType': 'always', 'condition': 'always'}]}
    if DATASET == 'investigation':
        value.update(name='冻结开放调查评测 · '+project.rsplit('-', 1)[-1],
                     instruction='按用户目标只读调查冻结证据；合成评测不提交任务成功或沉淀Skill。')
        node = value['nodes'][1]
        node['instruction'] = ('你执行隔离冻结开放调查。先加载 investigation_snapshot 的完整Schema，再读取用户指定场景。'
            '只读当前可信项目场景；不要把题名或目录当证据，不读取参考答案，不调用其他MCP、Task验收、Skill或记忆写入工具。'
            '判断观测支持的主要机制，保留不能确定的范围；这是合成沙箱，不能宣称确认了生产根因。'
            '内部输出一个JSON对象：caseId为实际场景，finding以简短自然中文说明主要支持的机制或证据不足，'
            'reason用中文列实际观测依据与不确定性，confirmedProductionRootCause必须为false。不要求用户填写JSON或英文标签。')
        node['config']['outputContract']['schema'] = {'type': 'object',
            'required': ['caseId', 'finding', 'reason', 'confirmedProductionRootCause'],
            'properties': {'caseId': {'type': 'string'}, 'finding': {'type': 'string', 'maxLength': 100},
                'reason': {'type': 'string'}, 'confirmedProductionRootCause': {'const': False}}}
    elif DATASET == 'skill':
        value.update(name='合成Skill结构审核 · '+project.rsplit('-', 1)[-1],
            instruction='只读审核合成来源或包文件；结构合规不等于实际任务验收、Skill发布或能力证明。')
        node = value['nodes'][1]
        node['instruction'] = ('先加载skill_snapshot完整Schema并读取用户指定场景。只读取当前项目；不读参考答案，不调用Task验收、'
            'Skill发布或长期记忆，不将合成记录变成已验证来源。来源结构规则：至少3个且不超过平台archive上限的声明成功记录，'
            '独立sourceId和episodeId，至少2种conditionKey，完整accepted-task-episode-v1正文及正确SHA256；'
            '正文projectId/runId/sessionId/sourceId/episodeId逐条匹配元数据，当前run与hash必须在集合中。'
            '这是结构审核，实际TaskAcceptance资格仍未建立。包规则：路径须相对且规范，无空段、点段、父路径或控制字符；'
            '只允许文本资源和规定二进制资产，二进制资产要求BASE64且不能作为脚本；文本要求UTF8、无二进制控制符；'
            '禁止可执行文件、明文秘密、私钥、肯定的审批绕过和脚本网络或资源写操作。环境变量秘密占位符、禁止绕过审批的规则允许。'
            '对实际输入给出内部JSON对象，caseId为本场景，decision只取“满足结构约束”或“必须拒绝”，reason写实际依据及限制；'
            '不得把满足结构约束称为正式发布或真实业务成功，用户不需要填写JSON。')
        node['config']['outputContract']['schema'] = {'type': 'object', 'required': ['caseId', 'decision', 'reason'],
            'properties': {'caseId': {'type': 'string'}, 'decision': {'enum': ['满足结构约束', '必须拒绝']},
                'reason': {'type': 'string'}}}
    return value


def prepare(output):
    manifest = verify_corpus()
    projects = {p['projectId']: p for p in API('/api/v1/admin/ops-projects/snapshot', token=TOKEN)['projects']}
    actor = next(u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN) if u['username'] == CREDENTIALS['username'])
    users_file = ROOT/('deploy/.acceptance-private/platform-'+DATASET+'-users.json')
    existing_users = {u['username']: u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN)}
    if users_file.exists():
        eval_credentials = json.loads(users_file.read_text())
    else:
        if any('ops_eval_'+DATASET+'_'+split in existing_users for split in ['dev', 'holdout']):
            raise RuntimeError('Existing evaluator identity has no retained credentials; preserved')
        eval_credentials = {split: {'username': 'ops_eval_'+DATASET+'_'+split, 'password': secrets.token_urlsafe(36)}
                            for split in ['dev', 'holdout']}
        users_file.write_text(json.dumps(eval_credentials)+'\n')
        users_file.chmod(0o600)
    for split, credentials in eval_credentials.items():
        if credentials['username'] not in existing_users:
            API('/api/v1/admin/admin-user/create', 'POST', {**credentials,
                'userId': credentials['username'], 'userRole': 'user', 'status': 1}, TOKEN)
    existing_users = {u['username']: u for u in API('/api/v1/admin/admin-user/query-all', token=TOKEN)}
    result = {'scope': manifest['scope'], 'manifest': manifest, 'definitions': {}}
    for project in sorted({case['projectId'] for case in json.loads((CORPUS/'facts.json').read_text())}):
        if project not in projects:
            API('/api/v1/admin/ops-projects/projects', 'POST', {'projectId': project,
                'name': '平台冻结巡检 '+project.rsplit('-', 1)[-1], 'owner': CREDENTIALS['username'],
                'description': 'SYNTHETIC_PLATFORM_EVALUATION_ONLY；项目记忆/工具与开发、留出隔离；不提交Skill来源验收。',
                'environments': ['sandbox']}, TOKEN)
        split = project.rsplit('-', 1)[-1]
        evaluator = existing_users[eval_credentials[split]['username']]
        member_path = '/api/v1/admin/ops-projects/projects/'+project+'/members'
        members = SEED['normalized_members'](API(member_path, token=TOKEN))
        if not any(m['userId'] == evaluator['userId'] for m in members):
            API(member_path, 'PUT', {'members': members+[{'userId': evaluator['userId'],
                'username': evaluator['username'], 'memberRole': 'MEMBER'}]}, TOKEN)
        isolation(project)
        path = '/api/v1/admin/ops/projects/'+project
        name = 'OPS platform frozen '+DATASET+' '+project.rsplit('-', 1)[-1]
        if manifest.get('configurationRevision'): name += ' '+CORPUS.name.rsplit('-', 1)[-1]
        endpoint = 'http://127.0.0.1:'+str(manifest.get('mcpPeerPort', PEERS[DATASET][0]))+'/mcp'
        integrations = [m for m in API(path+'/tools', token=TOKEN) if m['mcpName'] == name]
        if not integrations:
            API('/api/v1/admin/ops/tool-executions', 'POST', {'projectId': project, 'toolsetId': 'capability.manage',
                'toolName': 'mcp_server_import', 'userId': actor['userId'], 'authenticatedUsername': CREDENTIALS['username'],
                'executionScope': 'PRE_APPROVAL_WORKFLOW', 'runId': 'platform-eval-import-'+uuid.uuid4().hex,
                'arguments': {'sourceUrl': endpoint, 'capabilityName': name,
                              'credentialRef': '${env:OPS_ACCEPTANCE_MCP_TOKEN}', 'transportType': 'streamable-http'}}, TOKEN)
            integrations = [m for m in API(path+'/tools', token=TOKEN) if m['mcpName'] == name]
        if len(integrations) != 1:
            raise RuntimeError('Ambiguous frozen MCP; preserved')
        mcp = integrations[0]
        if mcp['status'] not in ('ENABLED', 'PENDING_REVIEW') or mcp['transportConfig']['endpoint'] != endpoint:
            raise RuntimeError('Changed/disabled frozen MCP; preserved')
        if [t['toolName'] for t in mcp['remoteTools']] != [DATASET+'_snapshot']:
            raise RuntimeError('Unexpected tool capability; no writes reviewed')
        policies = API(path+'/mcp-tool-policies', token=TOKEN)
        policy = next(p for p in policies if p['mcpId'] == mcp['mcpId'] and p['toolName'] == DATASET+'_snapshot')
        if policy['status'] == 'PENDING_REVIEW':
            policy = API(path+'/mcp-tool-policies/'+policy['policyId']+'/approve', 'POST', {
                'effectType': 'READ_EXTERNAL_STATE', 'effectScope': 'TARGET_RESOURCE_READ', 'mutability': 'READ_ONLY',
                'capability': 'READ_ONLY', 'allowedActions': [(DATASET+'_snapshot').upper()], 'riskLevel': 'LOW', 'readOnly': True,
                'investigateAllowed': True, 'prepareAllowed': False, 'landAllowed': False,
                'requiresApprovedPackage': False, 'requiresHumanApproval': False, 'requiresDryRun': False,
                'requiresRollbackPlan': False, 'disclosureTier': 'CORE',
                'reason': '用户已授权本机隔离平台评测；冻结题集已审核，源码只读facts，不挂参考答案，仅SQLite记录调用。'}, TOKEN)
        if policy['status'] != 'ACTIVE' or policy.get('readOnly') is not True or policy.get('landAllowed') is not False:
            raise RuntimeError('Unreviewed/changed policy; retained')
        if mcp['status'] != 'ENABLED':
            API(path+'/tools/'+mcp['mcpId']+'/status', 'PATCH', {'status': 'ENABLED'}, TOKEN)
        expected = definition(project, mcp['mcpId'])
        versions = API('/api/v1/admin/ops-agents/'+expected['agentId']+'/versions', token=TOKEN)
        if versions:
            current = versions[0]
            if current.get('instruction') != expected['instruction'] or current.get('nodes') != expected['nodes']:
                raise RuntimeError('Frozen agent changed; create a new reviewed corpus/configuration')
        else:
            current = API('/api/v1/admin/ops-agents/drafts', 'POST', expected, TOKEN)
        if current['lifecycle'] != 'PUBLISHED':
            for action in ['validate', 'publish']:
                current = API('/api/v1/admin/ops-agents/'+current['agentId']+'/versions/'+str(current['version'])+'/'+action, 'POST', {}, TOKEN)
        result['definitions'][project] = {**{k: current[k] for k in ['agentId', 'version', 'definitionHash']},
                                          'evaluatorUserId': evaluator['userId'], 'evaluatorUsername': evaluator['username']}
    result['peerSourceSha256'] = peer_evidence()['sourceSha256']
    if result['peerSourceSha256'] != manifest['factsSha256']:
        raise RuntimeError('MCP facts differ from frozen corpus')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': 'READY', 'cases': manifest.get('cases', manifest.get('totalCases')), 'definitions': result['definitions']}, ensure_ascii=False))


def run(prepared, output, split, repeats, limit):
    verify_corpus()
    setup = json.loads(prepared.read_text())
    evaluator_credentials = json.loads((ROOT/('deploy/.acceptance-private/platform-'+DATASET+'-users.json')).read_text())
    evaluator_tokens = {name: SEED['login'](credentials) for name, credentials in evaluator_credentials.items()}
    public = {c['caseId']: c for c in json.loads((CORPUS/'facts.json').read_text())}
    reference = [c for c in json.loads((CORPUS/'reference.json').read_text()) if split == 'all' or c['split'] == split]
    structure_proof = verify_skill_truth([case['caseId'] for case in reference])
    current_manifest = verify_corpus()
    if any(setup['manifest'][key] != current_manifest[key] for key in ['factsSha256', 'referenceSha256']):
        raise RuntimeError('Prepared configuration belongs to a different frozen corpus')
    attempts = [(c, index) for c in reference for index in range(1, repeats+1)]
    if limit:
        attempts = attempts[:limit]
    report = {'scope': setup['scope'], 'status': 'RUNNING', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
              'casesPlanned': len(reference), 'attemptsPlanned': len(attempts), 'repeats': repeats,
              'models': ['gpt-5.6-terra'], 'results': [], 'admittedAttempts': [],
              'layerCases': current_manifest.get('cases', current_manifest.get('totalCases')),
              'layerLimitation': current_manifest.get('limitation', current_manifest['scope']),
              'evaluationStage': 'FIVE_REPEAT_EVALUATION' if repeats == 5 and not limit else 'DEVELOPMENT_SCREENING',
              'attemptLimit': limit, 'artifacts': runtime_identity(),
              'factsSha256': setup['manifest']['factsSha256'], 'referenceSha256': setup['manifest']['referenceSha256'],
              'preparedReportSha256': hashlib.sha256(prepared.read_bytes()).hexdigest(), 'frozenDefinitions': setup['definitions']}
    report['peerIsolation'] = mount_isolation()
    if repeats == 5 and not limit:
        from platform_eval_binding import bind
        report['validationBinding'] = bind(TEST_SUMMARY, DEPLOYMENT_EVIDENCE,
            report['artifacts'], current_manifest, PRIOR_RESULTS)
        if DATASET == 'inspection':
            if INSPECTION_REVIEW is None:
                raise ValueError('Formal inspection requires the retained independent frozen corpus review')
            review_bytes = INSPECTION_REVIEW.read_bytes()
            review = json.loads(review_bytes)
            if (review.get('status') != 'APPROVED_REVISION_STATIC_ONLY'
                    or any(review.get(key) != current_manifest[key] for key in ['factsSha256', 'referenceSha256'])
                    or not review.get('checks') or not all(value is True for value in review['checks'].values())):
                raise ValueError('Inspection facts/reference or independent approval changed')
            report['independentCorpusReview'] = {'path': str(INSPECTION_REVIEW),
                'sha256': hashlib.sha256(review_bytes).hexdigest(), 'boundary': review.get('boundary')}
            report['comparisonScheme'] = setup.get('group', 'REACT_BASELINE_SAME_FROZEN_OBSERVATIONS')
    if DATASET == 'investigation': report['scoringRule'] = SCORING_REVIEW
    if DATASET == 'skill': report['structuralNativeProof'] = structure_proof
    try:
        for case, repetition in attempts:
            if STOP_REQUESTED_AT is not None:
                break
            record = public[case['caseId']]
            project = record['projectId']
            isolation_before = isolation(project)
            frozen = setup['definitions'].get(case['caseId'], setup['definitions'].get(project))
            if frozen is None: raise RuntimeError('Frozen case definition missing')
            run_token = evaluator_tokens[project.rsplit('-', 1)[-1]]
            session = API('/api/v1/user/chat/session', 'POST', {'projectId': project, 'agentId': frozen['agentId'],
                'agentVersion': frozen['version'], 'title': '合成平台巡检 · '+case['caseId']+' · '+str(repetition)}, run_token)
            started = time.time()
            report['activeAttempt'] = {'caseId': case['caseId'], 'repetition': repetition,
                'sessionId': session, 'stage': 'SUBMITTING_NATIVE_RUN'}
            output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            response = API('/api/v1/user/chat/sessions/'+session+'/messages', 'POST', {
                'projectId': project, 'query': record['query'], 'mode': 'AGENT', 'engine': 'HYBRID',
                'agentDefinitionId': frozen['agentId'], 'agentVersion': frozen['version'],
                'metadata': {'executionType': 'WORKFLOW', 'fixture': 'SYNTHETIC_PLATFORM_EVAL_NO_LEARNING'}}, run_token, timeout=600)
            run_id = response['metadata']['runId']
            report['activeAttempt'].update(runId=run_id, stage='AWAITING_NATIVE_TERMINAL')
            report['admittedAttempts'].append(dict(report['activeAttempt']))
            output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            deadline = time.monotonic()+600
            while True:
                state = rows("SELECT JSON_OBJECT('status',status,'hash',agent_definition_hash,'error',error_message,'userId',user_id,'durationMs',duration_ms) FROM ai_ops_agent_run WHERE run_id="+q(run_id))[0]
                if state['status'] in ('SUCCEEDED', 'FAILED', 'CANCELED'):
                    break
                if time.monotonic() > deadline:
                    raise TimeoutError('Actual platform attempt still pending: '+run_id)
                time.sleep(1)
            report['activeAttempt'].update(stage='READING_NATIVE_EVIDENCE', nativeStatus=state['status'])
            output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            checkpoints = rows('SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id='+q(run_id)+' ORDER BY checkpoint_seq')
            receipts = rows("SELECT JSON_OBJECT('hash',output_hash,'output',full_output,'status',status) FROM ai_ops_tool_result WHERE run_id="+q(run_id)+" AND source='MCP_REMOTE_TOOL' ORDER BY id")
            dispatches = rows("SELECT JSON_OBJECT('rpcId',request_id,'tool',tool_name,'attempt',physical_attempt) FROM ai_ops_workflow_tool_dispatch WHERE run_id="+q(run_id)+' ORDER BY id')
            business_calls = rows("SELECT JSON_OBJECT('callId',call_id,'tool',tool_name,'readOnly',read_only,'status',status) FROM ai_ops_mcp_tool_call WHERE run_id="+q(run_id)+' ORDER BY id')
            events = API('/api/v1/admin/ops-agent-runs/'+run_id+'/events/list', token=TOKEN)
            identity = [e.get('payload', {}) for e in events if e.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
            model_requests = [e.get('payload', {}) for e in events if e.get('eventType') == 'MODEL_REQUEST_ATTEMPT']
            peer = [r for r in peer_evidence()['calls'] if r['rpc_id'] in {d['rpcId'] for d in dispatches}]
            answer = None
            # Node outputs have a frozen context entry; inspect all persisted
            # nested objects without trusting text outside the actual checkpoint.
            def find(value):
                nonlocal answer
                if isinstance(value, dict):
                    if value.get('caseId') == case['caseId'] and ((DATASET == 'inspection' and value.get('conclusion') in ('健康', '异常', '证据不足', 'HEALTHY', 'UNHEALTHY', 'INSUFFICIENT_DATA'))
                            or (DATASET == 'investigation' and isinstance(value.get('finding'), str))
                            or (DATASET == 'skill' and value.get('decision') in ('满足结构约束', '必须拒绝'))):
                        answer = value
                    for child in value.values(): find(child)
                elif isinstance(value, list):
                    for child in value: find(child)
                elif isinstance(value, str) and value.lstrip().startswith(('{', '[')):
                    try: find(json.loads(value))
                    except ValueError: pass
            find(checkpoints)
            if DATASET == 'inspection':
                expected = {'健康': 'HEALTHY', '异常': 'UNHEALTHY', '证据不足': 'INSUFFICIENT_DATA'}[case['expectedLabel']]
                matches_answer = answer is not None and {'健康': 'HEALTHY', '异常': 'UNHEALTHY', '证据不足': 'INSUFFICIENT_DATA'}.get(answer['conclusion'], answer['conclusion']) == expected
            elif DATASET == 'investigation':
                expected = case['expectedFinding']
                finding_score = SCORING.score(answer, expected)
                matches_answer = finding_score['passed']
            else:
                expected = case['expectedLabel']
                matches_answer = answer is not None and answer['decision'] == expected and bool(str(answer.get('reason', '')).strip())
            receipt_matches = bool(receipts) and all(r['status'] == 'SUCCEEDED'
                and hashlib.sha256(r['output'].encode()).hexdigest() == r['hash']
                and hashlib.sha256(canonical(json.loads(r['output']).get('normalizedContent', {})).encode()).hexdigest() == case['factsSha256'] for r in receipts)
            checks = {'runSucceeded': state['status'] == 'SUCCEEDED', 'frozenDefinition': state['hash'] == frozen['definitionHash'],
                'creatorIdentityRetained': state['userId'] == frozen['evaluatorUserId'],
                'modelIdentityVerified': bool(identity) and all(i.get('requestedModel') == i.get('responseModel') == 'gpt-5.6-terra' for i in identity),
                'noInjectedCrossTaskMemory': not any(e.get('eventType') == 'MEMORY_CONTEXT_FINISHED'
                    and e.get('payload', {}).get('memoryContextChars', 0) != 0 for e in events),
                'expectedConclusion': matches_answer,
                'actualReceiptHashes': receipt_matches,
                'independentPeerHashes': bool(peer) and len(peer) == len(dispatches) and all(p['status'] == 'SUCCEEDED'
                    and p['facts_sha256'] == case['factsSha256'] and p['source_sha256'] == setup['manifest']['factsSha256'] for p in peer),
                'readOnlyToolBound': len(dispatches) >= case.get('minPhysicalToolCalls', 1) and len(dispatches) <= case['maxPhysicalToolCalls']
                    # Durable dispatch names identify the registered capability; the
                    # independent peer records the actual business tool name.
                    and len(business_calls) == len(peer) == len(dispatches)
                    and all(p['tool'] == DATASET+'_snapshot' for p in peer)
                    and all(c['readOnly'] == 1 and c['status'] == 'SUCCEEDED' for c in business_calls),
                'learningIsolation': isolation_before == isolation(project)}
            if setup.get('requiresReceiptBeforeModel'):
                completed_reads = [index for index, e in enumerate(events)
                    if e.get('eventType') == 'TOOL_CALL_FINISHED' and e.get('status') == 'SUCCEEDED'
                    and e.get('nodeId') == 'read-snapshot']
                first_model = next((index for index, e in enumerate(events)
                    if e.get('eventType') == 'MODEL_REQUEST_ATTEMPT'), -1)
                checks['specifiedPhysicalReadBeforeModel'] = bool(completed_reads) and first_model > min(completed_reads)
            result = {'caseId': case['caseId'], 'family': case['family'], 'split': case['split'], 'repetition': repetition,
                'runId': run_id, 'sessionId': session, 'durationSeconds': round(time.time()-started, 3),
                'runtimeDurationMs': state.get('durationMs'),
                'status': 'PASS' if all(checks.values()) else 'FAIL', 'checks': checks, 'expected': expected,
                'answer': answer, 'state': state, 'modelIdentities': identity, 'modelRequests': model_requests,
                'modelRequestAttempts': len(model_requests),
                'peerCalls': peer, 'dispatches': dispatches, 'businessCalls': business_calls, 'receipts': receipts}
            if DATASET == 'investigation': result['findingScore'] = finding_score
            report['results'].append(result)
            report['activeAttempt'] = None
            output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            output.with_name(output.stem+'-'+case['caseId']+'-'+str(repetition)+'-events.json').write_text(json.dumps(events, ensure_ascii=False, indent=2)+'\n')
            print(json.dumps({k: result[k] for k in ['caseId', 'repetition', 'runId', 'status', 'checks', 'durationSeconds']}, ensure_ascii=False), flush=True)
        report['status'] = ('PARTIAL_ADMISSION_STOP' if STOP_REQUESTED_AT is not None
                            and len(report['results']) < len(attempts)
                            else 'PASS' if all(r['status'] == 'PASS' for r in report['results']) else 'FAIL')
    except Exception as error:
        report['status'] = 'BLOCKED'
        report['error'] = type(error).__name__+': '+str(error)
        raise
    finally:
        report['finalRuntimeIdentity'] = runtime_identity()
        report['artifactUnchangedDuringRun'] = report['finalRuntimeIdentity']['deployedJarSha256'] == report['artifacts']['deployedJarSha256']
        if not report['artifactUnchangedDuringRun']:
            report['status'] = 'INVALID_ARTIFACT_CHANGED'
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        report['admissionControl'] = {'stopRequestedAt': STOP_REQUESTED_AT,
            'currentNativeRunAllowedToFinish': True,
            'unscoredAdmittedAttempts': len(report['admittedAttempts']) - len(report['results']),
            'unadmittedAttempts': len(attempts) - len(report['admittedAttempts']),
            'submissionOutcomeUnknown': bool(report.get('activeAttempt')
                and report['activeAttempt']['stage'] == 'SUBMITTING_NATIVE_RUN')}
        report['passedAttempts'] = sum(r['status'] == 'PASS' for r in report['results'])
        report['completedAttempts'] = len(report['results'])
        report['denominators'] = {'plannedUniqueCases': len(reference), 'plannedAttempts': len(attempts),
            'completedUniqueCases': len({r['caseId'] for r in report['results']}),
            'completedAttempts': report['completedAttempts'], 'passedAttempts': report['passedAttempts']}
        reliability = []
        for case in reference:
            actual = [r for r in report['results'] if r['caseId'] == case['caseId']]
            count, passed = len(actual), sum(r['status'] == 'PASS' for r in actual)
            reliability.append({'caseId': case['caseId'], 'attempts': count, 'passed': passed,
                **{f'pass^{k}': (math.comb(passed, k)/math.comb(count, k) if passed >= k else 0.0)
                    if count >= k else None for k in [1, 3, 5]}})
        report['caseReliability'] = reliability
        durations = sorted(r['runtimeDurationMs'] for r in report['results'] if isinstance(r.get('runtimeDurationMs'), (int, float)))
        report['runtimeP95Ms'] = durations[max(0, math.ceil(len(durations)*0.95)-1)] if durations else None
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        output.with_suffix('.sql').write_text('\n'.join(STATEMENTS)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--dataset', choices=['inspection', 'investigation', 'skill'], default='inspection')
    parser.add_argument('--corpus', type=Path, help='Explicit frozen corpus revision; previous evidence remains intact')
    parser.add_argument('--prepared', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--split', choices=['development', 'holdout', 'all'], default='development')
    parser.add_argument('--repeats', type=int, choices=[1, 5], default=1)
    parser.add_argument('--limit', type=int)
    parser.add_argument('--investigation-scoring-review', type=Path)
    parser.add_argument('--inspection-review', type=Path)
    parser.add_argument('--structure-review', type=Path)
    parser.add_argument('--native-truth', type=Path, action='append', default=[])
    parser.add_argument('--test-summary', type=Path, help='Fresh independently parsed complete regression bound to deployed JAR')
    parser.add_argument('--deployment', type=Path, help='Verified actual deployment bound to the same tested source/JAR')
    parser.add_argument('--reuse-results', type=Path, action='append', default=[], help='Retained prior model results from the same frozen corpus')
    args = parser.parse_args()
    DATASET = args.dataset
    STRUCTURE_REVIEW, NATIVE_TRUTH = args.structure_review, args.native_truth
    INSPECTION_REVIEW = args.inspection_review
    TEST_SUMMARY, DEPLOYMENT_EVIDENCE, PRIOR_RESULTS = args.test_summary, args.deployment, args.reuse_results
    if DATASET == 'investigation' and not args.prepare:
        SCORING, SCORING_REVIEW = load_investigation_scoring(args.investigation_scoring_review)
    CORPUS = args.corpus.resolve() if args.corpus else ROOT/('deploy/.acceptance-private/platform-'+DATASET+'-v1')
    if args.output.exists():
        parser.error('Retain previous attempts; use a new output path')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.prepare:
        prepare(args.output)
    elif args.prepared:
        signal.signal(signal.SIGUSR1, request_admission_stop)
        run(args.prepared, args.output, args.split, args.repeats, args.limit)
    else:
        parser.error('Use --prepare or supply an actual --prepared report')
