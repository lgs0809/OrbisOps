#!/usr/bin/env python3
"""Verify deployed Skill provenance against actual accepted tasks, without modifying any application rows.

Reads a retained proposal and applies the tested implementation in a MySQL read-only transaction.
This is not a new model task, publication, PATCH novelty check or SPLIT business acceptance.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def inspect(job, output):
    if any(p.exists() for p in (output, output.with_suffix('.sql'))):
        raise ValueError('Use a fresh evidence path')
    h = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    query = "SELECT JSON_OBJECT('input',input_json,'hash',plan_hash,'jobId',job_id) FROM ai_ops_skill_evolution_proposal WHERE project_id='ops-acceptance-a' AND job_id="+h['quoted'](job)
    snapshots = h['rows'](query)
    if len(snapshots)!=1:
        raise ValueError('An actual immutable proposal in the acceptance project is required')
    snapshot = snapshots[0]
    assert hashlib.sha256(snapshot['input'].encode()).hexdigest()==snapshot['hash']
    jar = ROOT/'server/orbisops-app/target/orbisops-app.jar'
    jar_hash = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed = subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1','sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
    assert deployed==jar_hash, 'The local tested application must match the running container'
    java_home = Path('/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.sql').write_text('-- Read-only actual proposal selection; Java also runs only SELECTs in START TRANSACTION READ ONLY.\n'+query+';\n')
    result = {'status':'FAIL','jobId':job,'jarSha256':jar_hash,'snapshotHash':snapshot['hash']}
    try:
        prefixes = ('orbisops-domain-','orbisops-infrastructure-','orbisops-types-','orbisops-application-',
                    'spring-jdbc-','spring-tx-','spring-core-','spring-beans-','spring-jcl-','mysql-connector-j-','slf4j-api-','fastjson-','fastjson2-')
        with tempfile.TemporaryDirectory(prefix='ops-portfolio-read-') as temporary:
            dependencies=[]
            with zipfile.ZipFile(jar) as packed:
                for name in packed.namelist():
                    if name.startswith('BOOT-INF/lib/') and Path(name).name.startswith(prefixes):
                        target=Path(temporary)/Path(name).name
                        target.write_bytes(packed.read(name));dependencies.append(str(target))
            classpath=os.pathsep.join(dependencies)
            source=ROOT/'scripts/InspectSkillSourcePortfolio.java'
            # The source launcher uses a separate class loader and cannot access a
            # package-private repository. Compile the read-only probe, then load
            # it with the exact nested modules from the tested/deployed JAR.
            compiled=subprocess.run([str(java_home/'bin/javac'),'--class-path',classpath,'-d',temporary,str(source)],
                                    text=True,capture_output=True,timeout=30)
            if compiled.returncode:
                result['failureType']='PortfolioProbeCompilationFailed'
                result['diagnostic']=compiled.stderr[-2000:]
                raise RuntimeError('Read-only probe compilation failed')
            command=[str(java_home/'bin/java'),'--class-path',os.pathsep.join([temporary,classpath]),
                     'cn.lgs.orbisops.infrastructure.adapter.repository.InspectSkillSourcePortfolio']
            password=subprocess.check_output(['docker','exec','orbisops-acceptance-mysql-1','sh','-c','printf "%s" "$MYSQL_ROOT_PASSWORD"'],text=True)
            env=dict(os.environ,OPS_PORTFOLIO_READ_PASSWORD=password)
            completed=subprocess.run(command,input=snapshot['input'],text=True,capture_output=True,env=env,timeout=90)
            if completed.returncode:
                # Only exception classification is retained; never record connection credentials.
                result['failureType']='JavaPortfolioReadFailed'
                result['diagnostic']=completed.stderr[-2000:]
                raise RuntimeError('Actual source-portfolio read failed; see retained evidence')
            result.update(json.loads(completed.stdout),command=command)
    finally:
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':result['status'],'sources':len(result['sources']),
                      'primary':len(result['primarySourceIds']),'relatedMethods':len(result['relatedSkillSourceGroups'])},ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--job',required=True)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    inspect(args.job,args.output)
