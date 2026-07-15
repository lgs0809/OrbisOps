#!/usr/bin/env python3
"""Run real Qwen routing against an isolated synthetic pgvector schema; retain resumable evidence.

No application business tables, release records, model names, budgets or existing schemas are changed.
"""
import argparse, hashlib, json, os, subprocess, tempfile, uuid, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output-dir',type=Path,required=True)
p.add_argument('--reuse-index-dir',type=Path,help='Reuse an isolated catalog with the same dataset; the runtime verifies each document/model generation again.')
p.add_argument('--queries',type=Path,help='Separate curated query file; preserves the original catalog and prior evidence.')
p.add_argument('--applicability',action='store_true',help='Exercise the production descriptive applicability adapter using the existing authorized Luna connection.')
p.add_argument('--runtime-container',action='store_true',help='Use the deployed backend network and existing credentials, with the same verified JAR. Temporary probe files are removed afterward.')
a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
dataset=ROOT/'scripts/fixtures/ops07-semantic-catalog.json'
jar=ROOT/'server/orbisops-app/target/orbisops-app.jar'
identity={'datasetSha256':hashlib.sha256(dataset.read_bytes()).hexdigest(),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
          'probeSha256':hashlib.sha256((ROOT/'scripts/acceptance/SemanticCatalogProbe.java').read_bytes()).hexdigest(),
          'realLunaApplicability':a.applicability,'runtimeContainer':a.runtime_container}
if a.queries:
    identity['queriesSha256']=hashlib.sha256(a.queries.read_bytes()).hexdigest()
manifest=a.output_dir/'manifest.json'
if manifest.exists():
    previous=json.loads(manifest.read_text())
    if any(previous[k]!=v for k,v in identity.items()): raise SystemExit('Dataset or tested runtime changed; choose a new output directory.')
    schema=previous['schema']
else:
    schema='ops07_semantic_'+uuid.uuid4().hex[:16]
    if a.reuse_index_dir:
        original=json.loads((a.reuse_index_dir/'manifest.json').read_text())
        if original['datasetSha256']!=identity['datasetSha256']:
            raise SystemExit('Existing index belongs to a different catalog.')
        schema=original['schema']
    manifest.write_text(json.dumps(dict(identity,schema=schema,scope='SYNTHETIC_CATALOG_REAL_MODELS'),indent=2)+'\n')
input_path=dataset
if a.queries:
    payload=json.loads(dataset.read_text());payload['queries']=json.loads(a.queries.read_text())['queries']
    input_path=a.output_dir/'input.json';input_path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n')
values={k:v.strip().strip('"').strip("'") for k,v in (line.split('=',1) for line in
        (ROOT/'deploy/.env.acceptance').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))}
env=dict(os.environ,PROBE_SCHEMA=schema,PROBE_PG_URL='jdbc:postgresql://127.0.0.1:15462/'+values['ORBISOPS_PGVECTOR_DATABASE'],
         PROBE_PG_USER=values.get('ORBISOPS_PGVECTOR_USERNAME','orbisops'),PROBE_PG_PASSWORD=values['ORBISOPS_PGVECTOR_PASSWORD'],
         RETRIEVAL_PROBE_KEY=json.loads((ROOT/'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey'])
if a.applicability:
    if values.get('ORBISOPS_AI_MODEL_CALLS_ENABLED')!='true': raise SystemExit('Authorized model calls are disabled.')
    env.update(PROBE_APPLICABILITY='true',PROBE_MODEL_BASE_URL=values['ORBISOPS_MODEL_BASE_URL'],
               PROBE_MODEL_API_KEY=values['ORBISOPS_MODEL_API_KEY'])
(a.output_dir/'inspect.sql').write_text(f'SET search_path TO {schema},public;\nSELECT status,COUNT(*) FROM ops_skill_route_generation GROUP BY status;\nSELECT g.skill_id,g.skill_version,g.model_identity,d.content_hash FROM ops_skill_route_generation g LEFT JOIN ops_skill_route_document d USING(generation_id) ORDER BY g.skill_id;\n')
with tempfile.TemporaryDirectory(prefix='orbisops-semantic-probe-') as directory:
    target=Path(directory)
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'): (target/Path(name).name).write_bytes(archive.read(name))
    classpath=str(target/'*')
    subprocess.run(['javac','-cp',classpath,'-d',str(target),str(ROOT/'scripts/acceptance/SemanticCatalogProbe.java')],check=True)
    if a.runtime_container:
        container='orbisops-acceptance-backend-1'
        deployed=subprocess.check_output(['docker','exec',container,'sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]
        if deployed!=identity['jarSha256']: raise SystemExit('Deploy the tested JAR before using its network/runtime.')
        remote='/tmp/orbisops-semantic-'+uuid.uuid4().hex
        subprocess.run(['docker','exec',container,'mkdir',remote],check=True)
        try:
            for compiled in target.glob('SemanticCatalogProbe*.class'):
                subprocess.run(['docker','cp',str(compiled),container+':'+remote+'/'],check=True)
            subprocess.run(['docker','cp',str(input_path),container+':'+remote+'/input.json'],check=True)
            runner='''import os,sys,zipfile,pathlib,subprocess
root=pathlib.Path(sys.argv[1]);libs=root/'lib';libs.mkdir()
with zipfile.ZipFile('/opt/orbisops/orbisops.jar') as archive:
 for name in archive.namelist():
  if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'): (libs/pathlib.Path(name).name).write_bytes(archive.read(name))
env=dict(os.environ,PROBE_SCHEMA=sys.argv[2],PROBE_PG_URL=os.environ['ORBISOPS_PGVECTOR_URL'].split('?')[0],
 PROBE_PG_USER=os.environ['ORBISOPS_PGVECTOR_USERNAME'],PROBE_PG_PASSWORD=os.environ['ORBISOPS_PGVECTOR_PASSWORD'],
 RETRIEVAL_PROBE_KEY=os.environ['ORBISOPS_SKILL_RETRIEVAL_API_KEY'],PROBE_RETRIEVAL_URL=os.environ['ORBISOPS_SKILL_RETRIEVAL_ENDPOINT'],
 PROBE_APPLICABILITY=sys.argv[3],PROBE_MODEL_BASE_URL=os.environ['ORBISOPS_MODEL_BASE_URL'],PROBE_MODEL_API_KEY=os.environ['ORBISOPS_MODEL_API_KEY'])
subprocess.run(['java','-Xmx256m','-cp',str(libs/'*')+':'+str(root),'SemanticCatalogProbe',str(root/'input.json'),str(root/'result.json')],env=env,check=True)
'''
            subprocess.run(['docker','exec','-i',container,'python3','-',remote,schema,str(a.applicability).lower()],input=runner,text=True,check=True)
            subprocess.run(['docker','cp',container+':'+remote+'/result.json',str(a.output_dir/'result.json')],check=True)
        finally:
            subprocess.run(['docker','exec',container,'rm','-rf','--',remote],check=True)
    else:
        # Local JDBC must not inherit the host JVM's SOCKS proxy. The container option checks the actual deployed network.
        subprocess.run(['java','-Xmx256m','-DsocksProxyHost=','-Dhttp.proxyHost=','-Dhttps.proxyHost=',
                        '-cp',classpath+os.pathsep+str(target),'SemanticCatalogProbe',str(input_path),str(a.output_dir/'result.json')],env=env,check=True)
result=json.loads((a.output_dir/'result.json').read_text())
print(json.dumps({'status':result['status'],'schema':schema,'readyGenerations':result['readyGenerations'],'queries':len(result['queries'])}))
raise SystemExit(0 if result['status']=='PASS' else 1)
