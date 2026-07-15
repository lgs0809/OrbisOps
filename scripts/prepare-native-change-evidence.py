#!/usr/bin/env python3
"""Bind an existing viewer and existing approved package; creates no permission or approval."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT=Path(__file__).resolve().parents[1]


def main(package,version,digest,output):
    if output.exists(): raise ValueError('Retain prior configuration evidence')
    h=runpy.run_path(str(ROOT/'scripts/seed-local-acceptance.py'))
    credentials=json.loads((ROOT/'deploy/.acceptance-private/users.json').read_text())['ops_acceptance_viewer']
    token=h['login'](credentials)
    path='/api/v1/user/ops/change-packages/'+package
    detail=h['request'](path,token=token)
    if (detail.get('projectId')!='ops-acceptance-a' or detail.get('approvedVersion')!=version
            or detail.get('approvedPackageHash')!=digest or detail.get('targetEnvironment')!='prod'):
        raise ValueError('Existing authorized native package does not match requested binding')
    service=detail['serviceId']
    binding={'projectId':detail['projectId'],'serviceId':service,'resourceKey':'service://'+service+'/prod',
             'packageId':package,'approvedVersion':version,'approvedPackageHash':digest}
    env=ROOT/'deploy/.env.acceptance'
    values=dict(line.split('=',1) for line in env.read_text().splitlines() if line and not line.startswith('#'))
    desired={'OPS_ACCEPTANCE_NATIVE_VIEWER_USERNAME':credentials['username'],
             'OPS_ACCEPTANCE_NATIVE_VIEWER_PASSWORD':credentials['password'],
             'OPS_ACCEPTANCE_NATIVE_PACKAGE_BINDING':json.dumps(binding,separators=(',',':'))}
    for key,value in desired.items():
        if key in values and values[key]!=value: raise ValueError('Existing reader binding differs; preserve it')
    with env.open('a') as stream:
        for key,value in desired.items():
            if key not in values: stream.write('\n'+key+'='+value+'\n')
    env.chmod(0o600)
    report={'status':'CONFIGURED_EXISTING_VIEWER_ONLY','binding':binding,'readPrincipal':credentials['username'],
            'newAccounts':0,'permissionMutations':0,'approvalMutations':0,
            'credentialEvidence':'existing private account copied only to private mode0600 deployment environment; no secret in evidence',
            'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',required=True)
    p.add_argument('--version',type=int,required=True);p.add_argument('--hash',required=True)
    p.add_argument('--output',type=Path,required=True);a=p.parse_args();main(a.package,a.version,a.hash,a.output)
