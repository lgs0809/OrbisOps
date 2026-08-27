#!/usr/bin/env python3
"""Exercise actual deployed applicability adapter on a retained Run snapshot, without seeding decisions."""
import argparse,json,os,subprocess,tempfile,zipfile,hashlib
from acceptance_model_contract import CURRENT_SMALL_MODEL
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--bundle',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
if a.output.exists():raise ValueError('Retain earlier evidence')
envvalues=dict(line.split('=',1) for line in (ROOT/'deploy/.env.acceptance').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
assert envvalues['ORBISOPS_AI_MODEL_CALLS_ENABLED'].strip().strip('"')=='true'
payload=json.loads(a.bundle.read_text());bundle=json.loads(payload[0]['bundle'])
if (bundle.get('projectId') != 'ops-acceptance-a' or not 1 <= len(bundle.get('skillCatalogRefs', [])) <= 20
        or not bundle.get('taskContext', {}).get('userRequest')):
    raise ValueError('A nonempty Skill catalog from an isolated acceptance run is required')
container='orbisops-acceptance-backend-1'
path='/opt/orbisops/orbisops.jar'
def deployed_hash():
    return subprocess.check_output(['docker','exec',container,'sha256sum',path],text=True).split()[0]
with tempfile.TemporaryDirectory(prefix='skill-applicability-') as directory:
 t=Path(directory)
 jar=t/'deployed.jar'
 fingerprint=deployed_hash()
 subprocess.run(['docker','cp',container+':'+path,str(jar)],check=True,capture_output=True)
 if hashlib.sha256(jar.read_bytes()).hexdigest()!=fingerprint:
  raise RuntimeError('The deployed JAR changed while obtaining the probe artifact')
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):(t/Path(n).name).write_bytes(z.read(n))
 (t/'input.json').write_text(json.dumps(bundle,ensure_ascii=False))
 cp=str(t/'*')
 subprocess.run(['javac','-cp',cp,'-d',str(t),str(ROOT/'scripts/acceptance/SkillApplicabilityProbe.java')],check=True)
 env=dict(os.environ,PROBE_MODEL_NAME=CURRENT_SMALL_MODEL,PROBE_MODEL_BASE_URL=envvalues['ORBISOPS_MODEL_BASE_URL'].strip().strip('"').strip("'"),PROBE_MODEL_API_KEY=envvalues['ORBISOPS_MODEL_API_KEY'].strip().strip('"').strip("'"))
 subprocess.run(['java','-Xmx128m','-DsocksProxyHost=','-Dhttps.proxyHost=','-Dhttp.proxyHost=','-cp',cp+os.pathsep+str(t),'SkillApplicabilityProbe',str(t/'input.json'),str(a.output)],env=env,check=True)
result=json.loads(a.output.read_text())
result.update(jarSha256=fingerprint, runtimeUnchanged=deployed_hash()==fingerprint, candidateCount=len(bundle['skillCatalogRefs']))
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
if not result['runtimeUnchanged']:raise SystemExit('The deployed JAR changed during the probe')
if result['status']=='FAIL':raise SystemExit(1)
