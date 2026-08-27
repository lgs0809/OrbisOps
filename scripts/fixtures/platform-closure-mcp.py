#!/usr/bin/env python3
"""MCP adapter to actual isolated HTTP resources; no reference labels or approvals.

Each connection is bound by its endpoint to one project/service. Independent
SQLite RPC records are linked to resource HTTP audit rows by X-Orbis-Rpc-Id.
Seed/fault/evidence control routes are never exposed as MCP tools.
"""
import datetime as dt
import hashlib
import hmac
import http.client
import json
import os
import sqlite3
import threading
import time
import urllib.parse
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN=os.environ['MCP_ACCEPTANCE_TOKEN']
TARGETS={'test':(8993,os.environ['PREPARE_CONTROL_TOKEN']), 'prod':(8994,os.environ['PROD_CONTROL_TOKEN'])}
PROJECTS={'ops-platform-closure-dev','ops-platform-closure-holdout', 'ops-closure-business-a', 'ops-acceptance-a'}
SESSIONS={}; LOCK=threading.Lock()
if min(len(TOKEN),*(len(token) for _,token in TARGETS.values()))<30: raise RuntimeError('LOCAL_CREDENTIAL_REQUIRED')
def canonical(value): return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))
def digest(value): return hashlib.sha256(canonical(value).encode()).hexdigest()
def db():
    connection=sqlite3.connect('/state/closure-mcp.sqlite',timeout=15); connection.row_factory=sqlite3.Row; return connection
with db() as connection:
    connection.executescript('''PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY, rpc_id TEXT, project_id TEXT,
        service TEXT, tool TEXT, input_hash TEXT, received_at REAL, completed_at REAL,
        status TEXT, result_hash TEXT);''')

def binding(path):
    uri=urllib.parse.urlparse(path)
    values=urllib.parse.parse_qs(uri.query)
    if uri.path!='/mcp' or set(values)!={'projectId','service'} or any(len(v)!=1 for v in values.values()):
        raise ValueError('BOUND_CONNECTION_REQUIRED')
    project,service=values['projectId'][0],values['service'][0]
    if project not in PROJECTS or not service.startswith('closure-') or len(service)>160 or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in service):
        raise ValueError('BOUND_CONNECTION_SCOPE_INVALID')
    return project,service

def tools(project,service):
    result=[]
    for environment in TARGETS:
        for action in ['read_state','check_orders','apply_configuration','lookup_receipt']+(['verify_observation','verify_preparation'] if environment=='test' else []):
            write=action=='apply_configuration'
            properties={'projectId':{'type':'string','enum':[project]},'service':{'type':'string','enum':[service]}}
            required=['projectId','service']
            if write:
                properties.update({key:{'type':'string','minLength':1,'maxLength':256} for key in ['expectedVersion','version','executionKey','deadline']})
                properties['expectedEpoch']={'type':'integer','minimum':0,'description':'实际读取的资源epoch，可选CAS前置条件；同版本发生修改或恢复后旧epoch仍必须拒绝。'}
                properties['configuration']={'type':'object','properties':{'failEvery':{'type':'integer','minimum':0,'maximum':20},'delayMs':{'type':'integer','minimum':0,'maximum':500}},'required':['failEvery','delayMs'],'additionalProperties':False}
                required+=['expectedVersion','version','configuration']
                if environment=='prod':required+=['executionKey','deadline']
            if action=='lookup_receipt':
                properties['executionKey']={'type':'string','minLength':1}; required+=['executionKey']
            if action=='verify_preparation':
                for key in ['baselineVersion','candidateVersion']:
                    properties[key]={'type':'string','minLength':1,'maxLength':256}
                for key in ['baselineConfiguration','candidateConfiguration']:
                    properties[key]={'type':'object','properties':{'failEvery':{'type':'integer','minimum':0,'maximum':20},'delayMs':{'type':'integer','minimum':0,'maximum':500}},'required':['failEvery','delayMs'],'additionalProperties':False}
                required+=['baselineVersion','candidateVersion','baselineConfiguration','candidateConfiguration']
            result.append({'name':environment+'_'+action,'description':f'实际本机隔离资源 {project} / {environment} / {service}；{action}。'
                +('使用版本CAS、期限和持久化执行键提交配置。模拟生产操作必须经平台Landing批准。' if write else '只读配置；check_orders执行20个实际订单GET。verify_observation前后读取同一资源版本、epoch、配置摘要，并执行20个GET，仅说明该实际观察区间。verify_preparation只读原生完整命令与前后状态、二十个GET、回滚记录及当前状态，缺少完整历史或任一不符返回FAILED；不执行或重放任何写。lookup_receipt查询已持久化回执，不重发写操作。'),
                'inputSchema':{'type':'object','properties':properties,'required':required,'additionalProperties':False},
                'outputSchema':{'type':'object','properties':{key:{'type':'string'} for key in ['status','projectId','environment','resourceKey']},'required':['status','projectId','environment','resourceKey']},
                'annotations':{'readOnlyHint':not write,'destructiveHint':write,'idempotentHint':action in ['read_state','lookup_receipt','apply_configuration']}})
    return result

def target_request(environment,path,rpc,scope,body=None):
    port,token=TARGETS[environment]
    connection=http.client.HTTPConnection('127.0.0.1',port,timeout=10)
    url=path+('?'+urllib.parse.urlencode(scope) if body is None else '')
    try:
        connection.request('GET' if body is None else 'POST',url,canonical(body) if body is not None else None,
            {'Authorization':'Bearer '+token,'Content-Type':'application/json','X-Orbis-Rpc-Id':rpc})
        response=connection.getresponse(); data=json.loads(response.read())
        if path!='/orders' and response.status>=400: raise ValueError(data.get('error','TARGET_REJECTED'))
        identity=data.get('state',{}) if path=='/evidence' else data
        if identity.get('resourceKey')!=scope['resourceKey'] or identity.get('environment')!=environment or identity.get('projectId')!=scope['projectId']:
            raise ValueError('TARGET_IDENTITY_MISMATCH')
        if 'configuration' in data: data['configurationDigest']=digest(data['configuration'])
        return response.status,data
    finally:connection.close()

def execute(name,inputs,project,service,rpc):
    environment,action=name.split('_',1)
    if environment not in TARGETS or action not in ['read_state','check_orders','apply_configuration','lookup_receipt','verify_observation','verify_preparation']:
        raise ValueError('UNKNOWN_TOOL')
    if inputs.get('projectId')!=project or inputs.get('service')!=service: raise ValueError('BOUND_RESOURCE_SCOPE_MISMATCH')
    scope={'projectId':project,'resourceKey':'service://'+service+'/'+environment}
    observed=lambda: target_request(environment,'/control/state',rpc,scope)[1]
    def orders():
        requests=[]
        for _ in range(20):
            status,data=target_request(environment,'/orders',rpc,scope)
            requests.append({'traceId':data['traceId'],'httpStatus':status,'version':data['version'],
                'method':'GET','path':'/orders','durationMs':data['durationMs']})
        return {**scope,'environment':environment,'status':'AVAILABLE','requests':requests,'requestCount':20,
            'errorCount':sum(r['httpStatus']!=200 for r in requests),
            'allRequestsSucceeded':all(r['httpStatus']==200 for r in requests),
            'requestMethod':'GET','routeDefinition':'/orders',
            'minimumRequestDurationMs':min(r['durationMs'] for r in requests),
            'totalRequestDurationMs':sum(r['durationMs'] for r in requests),
            'durationMethod':'Exact native monotonic measurement persisted for each traceId; minimum and sum over all 20 requests',
            'observedAt':dt.datetime.now(dt.timezone.utc).isoformat()}
    if action=='read_state':return observed()
    if action=='check_orders':return orders()
    if action=='lookup_receipt':return target_request(environment,'/control/receipt',rpc,{**scope,'executionKey':inputs['executionKey']})[1]
    if action=='verify_observation':
        if environment!='test':raise ValueError('TEST_OBSERVATION_REQUIRED')
        before=observed(); requests=orders(); after=observed()
        unchanged=all(before[key]==after[key] for key in ['resourceKey','projectId','environment','version','epoch','configurationDigest'])
        return {**requests,'beforeState':before,'afterState':after,'configurationUnchanged':unchanged,
            'observationStart':before['observedAt'],'observationEnd':after['observedAt'],
            'status':'PASSED' if unchanged and requests['errorCount']==0 else 'FAILED'}
    if action=='verify_preparation':
        if environment!='test':raise ValueError('TEST_VALIDATION_REQUIRED')
        evidence=target_request(environment,'/evidence',rpc,scope)[1]
        current=evidence['state'];current['configurationDigest']=digest(current['configuration'])
        receipts={r['execution_key']:r for r in evidence['receipt']};commands=[]
        for record in evidence['command_evidence']:
            command=json.loads(record['command_json']);before=json.loads(record['before_state_json']);after=json.loads(record['after_state_json'])
            receipt=receipts.get(record['execution_key'])
            if receipt is None or digest(command)!=receipt['command_hash'] or command['executionKey']!=record['execution_key']:
                raise ValueError('NATIVE_COMMAND_BINDING_INVALID')
            body=json.loads(receipt['body_json'])
            if any(body[k]!=after[k] for k in ['projectId','resourceKey','environment','version','epoch']) or after['epoch']!=before['epoch']+1:
                raise ValueError('NATIVE_STATE_BINDING_INVALID')
            if command['expectedVersion']!=before['version'] or command['version']!=after['version'] or command['configuration']!=after['configuration']:
                raise ValueError('NATIVE_COMMAND_STATE_INVALID')
            if any(s['projectId']!=project or s['resourceKey']!=scope['resourceKey'] or s['environment']!='test' for s in [before,after]):
                raise ValueError('NATIVE_RESOURCE_BINDING_INVALID')
            commands.append({'command':command,'beforeState':before,'afterState':after,'receipt':body,'createdAt':receipt['created_at'],'receiptHash':digest(body)})
        commands.sort(key=lambda c:c['afterState']['epoch'])
        checks={'completeCandidateAndRollbackHistory':len(commands)>=2}
        candidate,rollback=(commands[-2:] if len(commands)>=2 else [None,None]);orders=[]
        if candidate and rollback:
            checks.update({
                'baselineMatchesOriginal':candidate['beforeState']['version']==inputs['baselineVersion'] and candidate['beforeState']['configuration']==inputs['baselineConfiguration'],
                'candidateMatchesRequested':candidate['afterState']['version']==inputs['candidateVersion'] and candidate['afterState']['configuration']==inputs['candidateConfiguration'],
                'adjacentEpochs':rollback['beforeState']['epoch']==candidate['afterState']['epoch'] and rollback['afterState']['epoch']==candidate['afterState']['epoch']+1,
                'rollbackMatchesCandidateState':all(rollback['beforeState'][k]==candidate['afterState'][k] for k in ['version','epoch','configuration']),
                'rollbackRestoresOriginal':rollback['afterState']['version']==inputs['baselineVersion'] and rollback['afterState']['configuration']==inputs['baselineConfiguration'],
                'currentStateMatchesRestoredResource':all(current[k]==rollback['afterState'][k] for k in ['projectId','environment','resourceKey','epoch','version','configuration']),
                'distinctDurableExecutionKeys':candidate['receipt']['executionKey']!=rollback['receipt']['executionKey']})
            orders=sorted([r for r in evidence['request'] if candidate['createdAt']<=r['observed_at']<=rollback['createdAt']],key=lambda r:r['observed_at'])
            checks['atLeastTwentyActualCandidateGets']=len(orders)>=20 and sum(r['path']=='/orders' and r['method']=='GET' and r['http_status']==200 and candidate['createdAt']<=r['received_at']<=rollback['createdAt'] for r in evidence['audit_request'])>=20
            checks['everyObservedCandidateGetSucceeded']=bool(orders) and all(r['version']==inputs['candidateVersion'] and r['status']==200 for r in orders)
        return {**scope,'environment':'test','status':'PASSED' if all(checks.values()) else 'FAILED','checks':checks,
            'readOnly':True,'historySource':'ATOMIC_SQLITE_NATIVE_COMMAND_RECEIPT_AND_HTTP_LEDGER',
            'currentState':current,'candidate':candidate,'rollback':rollback,'requestCount':len(orders),'requests':orders,
            'evidenceHash':digest(evidence),'observedAt':dt.datetime.now(dt.timezone.utc).isoformat()}
    command={**scope,**{key:inputs[key] for key in ['expectedVersion','version','configuration']}}
    if 'expectedEpoch' in inputs:command['expectedEpoch']=inputs['expectedEpoch']
    for key in ['executionKey','deadline']:
        if key in inputs:command[key]=inputs[key]
        elif environment=='prod':raise ValueError('LANDING_AUTHORITY_REQUIRED')
    if environment=='test':
        command.setdefault('executionKey','prepare-'+rpc)
        command.setdefault('deadline',(dt.datetime.now(dt.timezone.utc)+dt.timedelta(seconds=60)).isoformat())
    return target_request(environment,'/control/apply',rpc,scope,command)[1]

class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def setup(self):super().setup();self.connection.settimeout(30)
    def log_message(self,*_):pass
    def send(self,status,value=None,headers=None):
        body=canonical(value).encode() if value is not None else b''
        self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)))
        for key,value in (headers or {}).items():self.send_header(key,value)
        self.end_headers()
        try:self.wfile.write(body)
        except (BrokenPipeError,ConnectionResetError):pass
    def authorized(self):return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+TOKEN)
    def do_GET(self):
        uri=urllib.parse.urlparse(self.path)
        if uri.path=='/ready':return self.send(200,{'status':'READY','scope':'ACTUAL_ISOLATED_RESOURCE_ADAPTER'})
        if not self.authorized():return self.send(403,{'error':'UNAUTHORIZED'})
        if uri.path=='/evidence':
            query=urllib.parse.parse_qs(uri.query);rpc=query.get('rpcId',[None])[0]
            with db() as connection:
                rows=connection.execute('SELECT * FROM calls'+(' WHERE rpc_id=?' if rpc else '')+' ORDER BY id',(rpc,) if rpc else ())
                return self.send(200,{'calls':[dict(r) for r in rows]})
        self.send(405)
    def do_DELETE(self):
        if not self.authorized():return self.send(403)
        with LOCK:SESSIONS.pop(self.headers.get('Mcp-Session-Id'),None)
        self.send(200)
    def do_POST(self):
        if not self.authorized():return self.send(403)
        call_id=None
        try:
            project,service=binding(self.path)
            size=int(self.headers.get('Content-Length',0))
            if not 0<size<=64000:return self.send(413)
            request=json.loads(self.rfile.read(size));method=request.get('method');params=request.get('params') or {}
            if method!='initialize' and SESSIONS.get(self.headers.get('Mcp-Session-Id'))!=(project,service):return self.send(404)
            if 'id' not in request:return self.send(202)
            headers={}
            if method=='initialize':
                session=uuid.uuid4().hex
                with LOCK:SESSIONS[session]=(project,service)
                headers['Mcp-Session-Id']=session
                result={'protocolVersion':params.get('protocolVersion','2024-11-05'),'capabilities':{'tools':{'listChanged':False}},'serverInfo':{'name':'orbisops-actual-local-closure','version':'1.0.0'}}
            elif method=='tools/list':result={'tools':tools(project,service)}
            elif method=='ping':result={}
            elif method=='tools/call':
                rpc=str(request['id'])
                with db() as connection:
                    call_id=connection.execute('INSERT INTO calls(rpc_id,project_id,service,tool,input_hash,received_at,status) VALUES(?,?,?,?,?,?,?)',(rpc,project,service,params.get('name'),digest(params),time.time(),'RECEIVED')).lastrowid
                try:
                    data=execute(params['name'],params.get('arguments') or {},project,service,rpc)
                    result={'isError':data.get('status')=='FAILED','content':[],'structuredContent':data}
                except (ValueError,KeyError,TypeError) as failure:
                    message=str(failure) if isinstance(failure,ValueError) else 'INVALID_TOOL_ARGUMENTS'
                    result={'isError':True,'content':[{'type':'text','text':message}]}
                with db() as connection:connection.execute('UPDATE calls SET status=?,completed_at=?,result_hash=? WHERE id=?',('REJECTED' if result['isError'] else 'COMPLETED',time.time(),digest(result),call_id))
            else:return self.send(200,{'jsonrpc':'2.0','id':request['id'],'error':{'code':-32601,'message':'UNKNOWN_METHOD'}})
            self.send(200,{'jsonrpc':'2.0','id':request['id'],'result':result},headers)
        except (OSError,http.client.HTTPException,TimeoutError):
            if call_id is not None:
                with db() as connection:connection.execute("UPDATE calls SET status='UNKNOWN',completed_at=? WHERE id=?",(time.time(),call_id))
            self.close_connection=True
        except (ValueError,KeyError,TypeError):self.send(400,{'error':'INVALID_REQUEST'})

server=ThreadingHTTPServer(('127.0.0.1',8995),Handler);server.daemon_threads=True;server.serve_forever()
