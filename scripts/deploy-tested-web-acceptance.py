#!/usr/bin/env python3
"""Deploy verified static artifacts with the existing isolated nginx image, retaining runtime data."""
from pathlib import Path
import argparse, hashlib, json, os, re, runpy, shutil, subprocess, tempfile, urllib.request
from web_asset_retention import retain
ROOT=Path(__file__).resolve().parents[1]

def require_deployment_budget(contract, configuration):
    """Compare resolved deployment settings with the running server before any web mutation."""
    budget=contract['modelRequestBudgetMs']
    values={name:configuration['services'][name]['environment'].get('ORBISOPS_MODEL_CALL_TIMEOUT_SECONDS')
            for name in ['backend','web']}
    if any(not re.fullmatch(r'[1-9][0-9]{0,6}',str(value or '')) or int(value)*1000!=budget
           for value in values.values()):
        raise RuntimeError('Configured backend/web model budget differs from the running server contract; align the deployment setting before rebuilding')
    env=dict(os.environ,ORBISOPS_MODEL_CALL_TIMEOUT_SECONDS=str(values['web']))
    derived=int(subprocess.check_output(['sh','-c',
        '. "$1"; printf "%s" "$ORBISOPS_MODEL_PROXY_TIMEOUT_SECONDS"','model-budget-preflight',
        str(ROOT/'web/15-model-response-timeout.envsh')],env=env,text=True))
    if derived*1000!=contract['proxyResponseTimeoutMs']:
        raise RuntimeError('Configured proxy derivation differs from the server contract; no web deployment was attempted')
    return derived

def main(log_path,output):
    if output.exists(): raise ValueError('Choose a new evidence filename; preserve previous deployments')
    log=re.sub(r'\x1b\[[0-9;]*m','',log_path.read_text())
    tests=re.search(r'Tests\s+(\d+) passed \((\d+)\)',log)
    if not tests or tests[1]!=tests[2] or 'hygiene passed' not in log or 'typecheck' not in log or not re.search(r'ready\s+built in',log):
        raise ValueError('A successful npm run verify log is required')
    dist=ROOT/'web/dist'
    files=sorted(p for p in dist.rglob('*') if p.is_file())
    if not (dist/'index.html').exists() or any(p.stat().st_mtime>log_path.stat().st_mtime for p in files):
        raise ValueError('Static artifacts must be from the supplied verified build')
    expected={p.relative_to(dist).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    acceptance=runpy.run_path(str(ROOT/'scripts/local-acceptance.py'))
    compose=acceptance['compose']
    verified=expected.copy()
    seed=runpy.run_path(str(ROOT/'scripts/seed-local-acceptance.py'))
    token=seed['login'](json.loads((ROOT/'deploy/.acceptance-private/admin.json').read_text()))
    contract=seed['request']('/api/v1/user/ops/task-acceptance/request-contract',token=token)
    budget=contract.get('modelRequestBudgetMs');client_budget=contract.get('draftRequestTimeoutMs');proxy_ms=contract.get('proxyResponseTimeoutMs')
    if not all(type(value) is int and value>0 for value in [budget,client_budget,proxy_ms]) or not budget<client_budget<proxy_ms or client_budget>2147483647:
        raise RuntimeError('Backend did not publish a valid bounded model request contract; deploy the verified server first')
    configuration=json.loads(subprocess.check_output(['docker','compose','--project-name',acceptance['PROJECT'],
        '--env-file',str(acceptance['ENV']),'-f','compose.yml','-f','deploy/compose.acceptance.yml',
        'config','--format','json'],cwd=ROOT,text=True))
    expected_proxy_budget=require_deployment_budget(contract,configuration)
    foundation=runpy.run_path(str(ROOT/'scripts/runtime_foundation.py'))['ensure_runtime_foundation'](
        'web','orbisops-acceptance-web-1')
    with tempfile.TemporaryDirectory(prefix='orbisops-web-') as folder:
        build=Path(folder)
        shutil.copytree(dist,build/'dist')
        previous=build/'previous'
        subprocess.run(['docker','cp','orbisops-acceptance-web-1:/usr/share/nginx/html',str(previous)],check=True)
        retention=retain(dist,previous,build/'dist','static')
        expected={p.relative_to(build/'dist').as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in (build/'dist').rglob('*') if p.is_file()}
        shutil.rmtree(previous)
        shutil.copy2(ROOT/'web/nginx.docker.conf',build/'nginx.conf')
        shutil.copy2(ROOT/'web/15-model-response-timeout.envsh',build/'15-model-response-timeout.envsh')
        (build/'Dockerfile').write_text('ARG BASE_IMAGE\nFROM ${BASE_IMAGE}\nRUN rm -rf /usr/share/nginx/html/*\nCOPY dist/ /usr/share/nginx/html/\nCOPY nginx.conf /etc/nginx/templates/default.conf.template\nCOPY --chmod=755 15-model-response-timeout.envsh /docker-entrypoint.d/15-model-response-timeout.envsh\n')
        subprocess.run(['docker','build','--pull=false','--network=none','--build-arg','BASE_IMAGE='+foundation['tag'],'-t','orbisops/web:2.0.0-acceptance',folder],check=True)
    # Compose's health gate avoids treating container creation as listener readiness.
    compose('up','-d','--no-deps','--no-build','--wait','--wait-timeout','60','web')
    subprocess.run(['docker','exec','orbisops-acceptance-web-1','nginx','-t'],check=True)
    template=ROOT/'web/nginx.docker.conf'
    deployed_template=subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','cat',
        '/etc/nginx/templates/default.conf.template'])
    if deployed_template!=template.read_bytes(): raise RuntimeError('Deployed nginx template differs from source')
    budget_script=ROOT/'web/15-model-response-timeout.envsh'
    if subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','cat',
            '/docker-entrypoint.d/15-model-response-timeout.envsh'])!=budget_script.read_bytes():
        raise RuntimeError('Deployed model timeout derivation differs from source')
    proxy_budget=int(subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','sh','-c',
        '. /docker-entrypoint.d/15-model-response-timeout.envsh; printf "%s" "$ORBISOPS_MODEL_PROXY_TIMEOUT_SECONDS"'],text=True))
    rendered=subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','cat','/etc/nginx/conf.d/default.conf'],text=True)
    if f'proxy_read_timeout {proxy_budget}s;' not in rendered or f'proxy_send_timeout {proxy_budget}s;' not in rendered:
        raise RuntimeError('Rendered proxy does not cover the deployed model budget')
    if proxy_budget!=expected_proxy_budget or proxy_budget*1000!=proxy_ms:
        raise RuntimeError('Server and web model budgets differ; align ORBISOPS_MODEL_CALL_TIMEOUT_SECONDS before testing')
    proxy_request=urllib.request.Request('http://127.0.0.1:3302/api/v1/user/ops/task-acceptance/request-contract',
                                        headers={'Authorization':'Bearer '+token})
    with urllib.request.urlopen(proxy_request,timeout=12) as response:
        proxy_result=json.load(response)
    if proxy_result.get('code')!='0000':raise RuntimeError('Browser model contract request failed')
    proxy_contract=proxy_result.get('data')
    if proxy_contract!=contract:raise RuntimeError('Browser API proxy and backend model budget contract differ')
    with urllib.request.urlopen('http://127.0.0.1:3302/api/v1/setup/status',timeout=10) as response:
        if response.status!=200: raise RuntimeError('Browser API proxy is unavailable')
    listing=subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','sh','-c',
        'cd /usr/share/nginx/html && find . -type f -exec sha256sum {} +'],text=True)
    actual={line.split('  ',1)[1].removeprefix('./'):line.split('  ',1)[0] for line in listing.splitlines()}
    if actual!=expected: raise RuntimeError('Deployed web bytes differ from verified dist')
    image=subprocess.check_output(['docker','inspect','orbisops-acceptance-web-1','--format','{{.Image}}'],text=True).strip()
    info=json.loads(subprocess.check_output(['docker','image','inspect',image],text=True))[0]
    result={'status':'PASS','tests':int(tests[1]),'webImage':image,'imageLayers':len(info['RootFS']['Layers']),
        'runtimeFoundation':foundation,
        'imageSizeBytes':info['Size'],'testLogSha256':hashlib.sha256(log_path.read_bytes()).hexdigest(),'staticFiles':verified,'assetRetention':retention,'deployedBytesMatch':True,'backendRestarted':False,
        'nginxTemplateSha256':hashlib.sha256(deployed_template).hexdigest(),'browserApiProxyStatus':200,
        'modelProxyTimeoutSeconds':proxy_budget,'timeoutScriptSha256':hashlib.sha256(budget_script.read_bytes()).hexdigest()}
    result['serverModelRequestContract']=contract;result['serverWebProxyBudgetMatch']=True
    result['budgetCheckedBeforeWebMutation']=True
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='staticFiles'}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--test-log',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    a=p.parse_args();main(a.test_log,a.output)
