"""Existing viewer GETs only. Fixed binding is deployment-owned, never model-owned.

Authenticated native records are hash-bound evidence, not digital signatures.
Raw HTTP responses are archived independently; the bounded projection retains
original native identities without duplicating entire version snapshots.
"""
import hashlib
import json
import re
import urllib.request

ORIGIN = 'http://127.0.0.1:8099'
MAX_NATIVE_RESPONSE_BYTES = 8 * 1024 * 1024


def fetch(path, token=None, credentials=None):
    headers = {'Content-Type':'application/json'}
    if token: headers['Authorization'] = 'Bearer '+token
    data = json.dumps(credentials).encode() if credentials is not None else None
    request = urllib.request.Request(ORIGIN+path,data,headers,method='POST' if credentials is not None else 'GET')
    with urllib.request.urlopen(request,timeout=8) as response:
        raw = response.read(MAX_NATIVE_RESPONSE_BYTES + 1)
        status = response.status
    if len(raw)>MAX_NATIVE_RESPONSE_BYTES: raise ValueError('NATIVE_READ_RESPONSE_TOO_LARGE')
    envelope = json.loads(raw)
    if status!=200 or envelope.get('code')!='0000': raise ValueError('NATIVE_READ_UNAVAILABLE')
    return envelope['data'],raw,status


def bound_projection(binding, bodies):
    package,version,digest = binding['packageId'],binding['approvedVersion'],binding['approvedPackageHash']
    detail = bodies['detail']
    if any(detail.get(k)!=v for k,v in [('projectId',binding['projectId']),('packageId',package),
            ('serviceId',binding['serviceId']),('approvedVersion',version),('approvedPackageHash',digest)]):
        raise ValueError('NATIVE_PACKAGE_BINDING_MISMATCH')
    versions = [v for v in bodies['versions'] if v.get('version')==version and v.get('packageHash')==digest]
    if len(versions)!=1: raise ValueError('NATIVE_APPROVED_VERSION_AMBIGUOUS')
    snapshot = versions[0].get('snapshotJson',versions[0].get('snapshot_json'))
    if isinstance(snapshot,str): snapshot=json.loads(snapshot)
    if not isinstance(snapshot,dict) or any(snapshot.get(k)!=v for k,v in
            [('packageId',package),('projectId',binding['projectId']),('version',version),('packageHash',digest)]):
        raise ValueError('NATIVE_APPROVED_SNAPSHOT_MISMATCH')
    approvals=[]
    landed=[]
    for event in bodies['events']:
        payload=event.get('payload',{})
        if event.get('eventType') in ('PACKAGE_APPROVAL_RECORDED','PACKAGE_APPROVED'):
            event_version=payload.get('approvedVersion',payload.get('version'))
            event_hash=payload.get('approvedPackageHash',payload.get('packageHash'))
            if event_version==version and event_hash==digest and payload.get('packageId')==package:
                approvals.append({k:event.get(k) for k in ('eventId','eventType','actor','createTime')}
                    | {k:payload[k] for k in ('approvedVersion','version','approvedPackageHash',
                        'packageHash','approvedBy','approvedCount','requiredApprovals') if k in payload})
        if event.get('eventType')=='LANDING_SUCCEEDED' and payload.get('approvedPackageVersion')==version \
                and payload.get('approvedPackageHash')==digest:
            landed.append(event)
    operations=[]
    for op in bodies['operations']:
        if any(op.get(k)!=v for k,v in [('projectId',binding['projectId']),('packageId',package),
                ('approvedVersion',version),('approvedPackageHash',digest),('resourceKey',binding['resourceKey'])]):
            raise ValueError('NATIVE_OPERATION_BINDING_MISMATCH')
        item={k:op.get(k) for k in ('operationRunId','landingRunId','operationId','operationHash',
            'approvedVersion','approvedPackageHash','resourceKey','status','factStatus','reasonCode',
            'resultId','outputHash','startedEpoch','finishedEpoch','startedEpochSource','finishedEpochSource')}
        result=op.get('result') or {}
        item['executionKey']=result.get('executionKey')
        if result and any(result.get(k)!=op.get(k) for k in ('resultId','outputHash')):
            raise ValueError('NATIVE_OPERATION_RECEIPT_MISMATCH')
        operations.append(item)
    postchecks=[]
    for event in landed:
        for check in event['payload'].get('independentVerification',{}).get('checks',[]):
            for leaf in check.get('checks',[]):
                if leaf.get('resourceKey')!=binding['resourceKey']:
                    raise ValueError('NATIVE_POSTCHECK_SCOPE_MISMATCH')
                receipt=leaf.get('readReceipt') or {}
                content=receipt.get('normalizedContent') or {}
                if content and any(content.get(k)!=v for k,v in [('projectId',binding['projectId']),
                        ('resourceKey',binding['resourceKey']),('environment','prod')]):
                    raise ValueError('NATIVE_POSTCHECK_RECEIPT_SCOPE_MISMATCH')
                postchecks.append({k:leaf.get(k) for k in ('operationId','operationHash',
                    'executionResultId','executionOutputHash','readStartedAt','readFinishedAt','passed','reasonCode')}
                    | {'readReceipt':{k:receipt.get(k) for k in ('resultId','outputHash','providerResultId',
                        'providerOutputHash','remoteToolName')},
                       'targetObservation':{k:content[k] for k in ('projectId','environment','resourceKey',
                        'status','version','scenario','observedAt','requestCount','errorCount') if k in content}})
    return {'nativePackage':{k:detail.get(k) for k in ('packageId','projectId','serviceId',
                'status','approvedVersion','approvedPackageHash')},
            'originalApprovedCriteria':snapshot.get('verificationCriteriaJson'),
            'approvalEvents':approvals,'operations':operations,'independentPostChecks':postchecks,
            'proofMeaning':'authenticated native read projection; original records archived; not a digital signature or final SLO verdict'}


def authorize(binding, credentials, selected):
    if any(selected.get(k)!=v for k,v in [('projectId',binding['projectId']),
            ('serviceId',binding['serviceId']),('environment','prod')]):
        raise ValueError('NATIVE_READ_SCOPE_MISMATCH')
    package=binding['packageId']
    if not re.fullmatch(r'cp-[a-zA-Z0-9-]{1,100}',package): raise ValueError('NATIVE_PACKAGE_ID_INVALID')
    if credentials.get('username')!='ops_acceptance_viewer': raise ValueError('EXISTING_VIEWER_REQUIRED')


def read(binding,credentials,selected,archive):
    authorize(binding, credentials, selected)
    package=binding['packageId']
    login,_,_=fetch('/api/v1/admin/admin-user/login',credentials=credentials)
    token=login['token'];bodies={};responses=[]
    for key,suffix in [('detail',''),('versions','/versions'),('events','/events'),('operations','/landing-operation-runs')]:
        path='/api/v1/user/ops/change-packages/'+package+suffix
        body,raw,status=fetch(path,token=token)
        digest=hashlib.sha256(raw).hexdigest()
        archive(path,status,digest,raw.decode('utf-8'))
        bodies[key]=body;responses.append({'path':path,'httpStatus':status,'responseSha256':digest})
    return {**bound_projection(binding,bodies),'nativeReadResponses':responses,
            'nativeReadPrincipal':credentials['username']}
