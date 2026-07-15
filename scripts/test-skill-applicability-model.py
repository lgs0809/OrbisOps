#!/usr/bin/env python3
"""Exercise actual deployed applicability adapter on a retained Run snapshot, without seeding decisions."""
import argparse,json,os,subprocess,tempfile,zipfile,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--bundle',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
if a.output.exists():raise ValueError('Retain earlier evidence')
envvalues=dict(line.split('=',1) for line in (ROOT/'deploy/.env.acceptance').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
jar=ROOT/'server/orbisops-app/target/orbisops-app.jar'
assert envvalues['ORBISOPS_AI_MODEL_CALLS_ENABLED'].strip().strip('"')=='true'
payload=json.loads(a.bundle.read_text());bundle=json.loads(payload[0]['bundle'])
with tempfile.TemporaryDirectory(prefix='skill-applicability-') as directory:
 t=Path(directory)
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):(t/Path(n).name).write_bytes(z.read(n))
 (t/'input.json').write_text(json.dumps(bundle,ensure_ascii=False))
 cp=str(t/'*')
 subprocess.run(['javac','-cp',cp,'-d',str(t),str(ROOT/'scripts/acceptance/SkillApplicabilityProbe.java')],check=True)
 env=dict(os.environ,PROBE_MODEL_BASE_URL=envvalues['ORBISOPS_MODEL_BASE_URL'].strip().strip('"').strip("'"),PROBE_MODEL_API_KEY=envvalues['ORBISOPS_MODEL_API_KEY'].strip().strip('"').strip("'"))
 subprocess.run(['java','-Xmx128m','-DsocksProxyHost=','-Dhttps.proxyHost=','-Dhttp.proxyHost=','-cp',cp+os.pathsep+str(t),'SkillApplicabilityProbe',str(t/'input.json'),str(a.output)],env=env,check=True)
result=json.loads(a.output.read_text());result['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest();a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
if result['status']=='FAIL':raise SystemExit(1)
