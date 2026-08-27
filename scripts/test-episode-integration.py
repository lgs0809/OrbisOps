#!/usr/bin/env python3
"""Run unchanged Episode assertions against a fresh retained schema on the existing loopback MySQL.

Model decisions and clock are explicit component fixtures. No production chat, success,
approval or Skill source is seeded. The runtime container and its business DB are retained.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--java-home',required=True,type=Path)
a=p.parse_args();out=a.output.resolve()
if any(out.with_suffix(s).exists() for s in ['.json','.log','.seed.sql','.xml']):
    raise ValueError('Fresh output required; never replace earlier evidence')
out.parent.mkdir(parents=True,exist_ok=True)
values=dict(line.split('=',1) for line in (ROOT/'deploy/.env.acceptance').read_text().splitlines() if line and not line.startswith('#'))
database='orbisops_episode_regression_'+uuid.uuid4().hex[:12]
user=values['ORBISOPS_MYSQL_USERNAME']
if not user.replace('_','').isalnum():raise ValueError('Unexpected configured test database user')
seed=f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nGRANT ALL ON `{database}`.* TO '{user}'@'%';\n"
out.with_suffix('.seed.sql').write_text('-- Isolated component schema; retained after execution.\n'+seed)
applied=subprocess.run(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=seed,text=True,capture_output=True)
out.with_suffix('.seed.log').write_text(applied.stdout+applied.stderr);applied.check_returncode()
jar=ROOT/'server/orbisops-app/target/orbisops-app.jar';jar_data=jar.read_bytes()
digest=hashlib.sha256(jar_data).hexdigest()
running=subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
if digest!=running:raise ValueError('Local immutable JAR must match the running deployment before testing')

def classes():
    result={}
    for module in (ROOT/'server').glob('orbisops-*'):
        path=module/'target/classes'
        for f in path.rglob('cn/lgs/orbisops/**/*.class'):
            name=str(f.relative_to(path));h=hashlib.sha256(f.read_bytes()).hexdigest()
            if name in result and result[name]!=h:raise ValueError('Conflicting production class')
            result[name]=h
    return result

runtime={}
def archive_classes(data,boot=False):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            canonical=name.removeprefix('BOOT-INF/classes/')
            if canonical.startswith('cn/lgs/orbisops/') and canonical.endswith('.class'):
                runtime[canonical]=hashlib.sha256(z.read(name)).hexdigest()
            elif boot and name.startswith('BOOT-INF/lib/orbisops-') and name.endswith('.jar'):
                archive_classes(z.read(name))
archive_classes(jar_data,True)
before=classes()
if before!=runtime:raise ValueError('Compiled production classes must match the deployed immutable artifact')
test=ROOT/'server/orbisops-app/src/test/java/cn/lgs/orbisops/infrastructure/adapter/repository/TaskEpisodeMySqlTest.java'
test_before=hashlib.sha256(test.read_bytes()).hexdigest()
command=['mvn','-B','-DsocksNonProxyHosts=localhost|127.*|[::1]','-pl','orbisops-app','-am','-Dtest=TaskEpisodeMySqlTest','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m -XX:ActiveProcessorCount=2','test']
env=dict(os.environ,JAVA_HOME=str(a.java_home),PATH=str(a.java_home/'bin')+os.pathsep+os.environ['PATH'],MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',
    ORBISOPS_EPISODE_TEST_URL='jdbc:mysql://127.0.0.1:'+values['ORBISOPS_MYSQL_PORT']+'/'+database+'?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC',
    ORBISOPS_EPISODE_TEST_USER=user,ORBISOPS_EPISODE_TEST_PASSWORD=values['ORBISOPS_MYSQL_PASSWORD'])
started=datetime.now(timezone.utc)
with out.with_suffix('.log').open('w') as log:
    result=subprocess.run(command,cwd=ROOT/'server',env=env,stdout=log,stderr=subprocess.STDOUT)
xml=ROOT/'server/orbisops-app/target/surefire-reports/TEST-cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeMySqlTest.xml'
suite=None;fresh=False
if xml.is_file():
    out.with_suffix('.xml').write_bytes(xml.read_bytes());suite=ET.fromstring(xml.read_bytes())
    fresh=xml.stat().st_mtime>=started.timestamp()
counts={k:int(suite.attrib[k]) for k in ['tests','failures','errors','skipped']} if suite is not None else {}
checks={'mavenExitedSuccessfully':result.returncode==0,'freshNativeSuite':fresh,'allTwentyTwoCasesActuallyExecuted':counts.get('tests')==22 and all(counts.get(k)==0 for k in ['failures','errors','skipped']),
    'productionClassBytesUnchangedAndEqualDeployedJar':classes()==before==runtime,'testSourceStable':hashlib.sha256(test.read_bytes()).hexdigest()==test_before,
    'immutableJarAndRuntimeUnchanged':hashlib.sha256(jar.read_bytes()).hexdigest()==digest==subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]}
report=dict(status='PASS_REAL_MYSQL_EPISODE_COMPONENT' if all(checks.values()) else 'FAIL_REAL_MYSQL_EPISODE_COMPONENT',recordedAt=datetime.now(timezone.utc).isoformat(),startedAt=started.isoformat(),database=database,command=command,counts=counts,checks=checks,jarSha256=digest,testSourceSha256=test_before,
    productionClasses=len(runtime),productionClassInventorySha256=hashlib.sha256(json.dumps(runtime,sort_keys=True).encode()).hexdigest(),
    boundary='Actual existing loopback MySQL, fresh retained minimal schema and unchanged assertions; deterministic model decisions and virtual clock for 24-hour/backoff cases, not real Luna semantics or a wall-clock day. Real concurrent database workers execute. Production classes match the running artifact; no service restart or business data writes.')
out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(status=report['status'],database=database,counts=counts,failed=[k for k,v in checks.items() if not v])))
raise SystemExit(0 if all(checks.values()) else 1)
