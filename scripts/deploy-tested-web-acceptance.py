#!/usr/bin/env python3
"""Deploy verified static artifacts with the existing isolated nginx image, retaining runtime data."""
from pathlib import Path
import argparse, hashlib, json, re, runpy, shutil, subprocess, tempfile, urllib.request
from web_asset_retention import retain
ROOT=Path(__file__).resolve().parents[1]

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
    compose=runpy.run_path(str(ROOT/'scripts/local-acceptance.py'))['compose']
    verified=expected.copy()
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
        (build/'Dockerfile').write_text('FROM orbisops/web:2.0.0\nRUN rm -rf /usr/share/nginx/html/*\nCOPY dist/ /usr/share/nginx/html/\nCOPY nginx.conf /etc/nginx/templates/default.conf.template\n')
        subprocess.run(['docker','build','--pull=false','-t','orbisops/web:2.0.0-acceptance',folder],check=True)
    # Compose's health gate avoids treating container creation as listener readiness.
    compose('up','-d','--no-deps','--no-build','--wait','--wait-timeout','60','web')
    subprocess.run(['docker','exec','orbisops-acceptance-web-1','nginx','-t'],check=True)
    template=ROOT/'web/nginx.docker.conf'
    deployed_template=subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','cat',
        '/etc/nginx/templates/default.conf.template'])
    if deployed_template!=template.read_bytes(): raise RuntimeError('Deployed nginx template differs from source')
    with urllib.request.urlopen('http://127.0.0.1:3302/api/v1/setup/status',timeout=10) as response:
        if response.status!=200: raise RuntimeError('Browser API proxy is unavailable')
    listing=subprocess.check_output(['docker','exec','orbisops-acceptance-web-1','sh','-c',
        'cd /usr/share/nginx/html && find . -type f -exec sha256sum {} +'],text=True)
    actual={line.split('  ',1)[1].removeprefix('./'):line.split('  ',1)[0] for line in listing.splitlines()}
    if actual!=expected: raise RuntimeError('Deployed web bytes differ from verified dist')
    image=subprocess.check_output(['docker','inspect','orbisops-acceptance-web-1','--format','{{.Image}}'],text=True).strip()
    info=json.loads(subprocess.check_output(['docker','image','inspect',image],text=True))[0]
    result={'status':'PASS','tests':int(tests[1]),'webImage':image,'imageLayers':len(info['RootFS']['Layers']),
        'imageSizeBytes':info['Size'],'testLogSha256':hashlib.sha256(log_path.read_bytes()).hexdigest(),'staticFiles':verified,'assetRetention':retention,'deployedBytesMatch':True,'backendRestarted':False,
        'nginxTemplateSha256':hashlib.sha256(deployed_template).hexdigest(),'browserApiProxyStatus':200}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='staticFiles'}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--test-log',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    a=p.parse_args();main(a.test_log,a.output)
