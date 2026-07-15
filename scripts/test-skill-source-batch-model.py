#!/usr/bin/env python3
"""Real configured Terra + exact deployed coordinator, explicit synthetic 21-source fixture, no business writes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
from deployed_repository_probe import ROOT, JAVA_HOME


def run(output, ledger, stop):
    if output.exists() or output.with_suffix('.fixture.json').exists():
        raise ValueError('Retain previous evidence; choose a new output filename')
    output.parent.mkdir(parents=True, exist_ok=True)
    jar = ROOT/'server/orbisops-app/target/orbisops-app.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed = subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1',
        'sha256sum','/opt/orbisops/orbisops.jar'], text=True).split()[0]
    if digest != deployed:
        raise RuntimeError('Use the exact tested and deployed application')
    catalog_query = """SELECT JSON_OBJECT('modelId',m.model_id,'apiId',m.api_id,'modelName',m.model_name,
        'baseUrl',a.base_url,'keyReference',a.api_key,'path',a.completions_path)
        FROM ai_client_model m JOIN ai_client_api a ON a.api_id=m.api_id
        WHERE m.model_name='gpt-5.6-terra' AND m.status=1 AND a.status=1;"""
    raw = subprocess.check_output(['docker','exec','-i','orbisops-acceptance-mysql-1','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B --raw orbisops_acceptance'],input=catalog_query,text=True)
    bindings = [json.loads(line) for line in raw.splitlines() if line.strip()]
    if len(bindings) != 1:
        raise RuntimeError('The existing exact Terra binding is required')
    binding = bindings[0]
    container = json.loads(subprocess.check_output(['docker','inspect','orbisops-acceptance-backend-1'],text=True))[0]
    values = dict(item.split('=',1) for item in container['Config']['Env'])
    if values.get('ORBISOPS_AI_MODEL_CALLS_ENABLED') != 'true':
        raise RuntimeError('The deployed model calls must already be enabled')
    key = binding.pop('keyReference')
    key = re.sub(r'\$\{env:([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}',
                 lambda m: values.get(m.group(1),m.group(2) or ''),key)
    binding['enabled'] = True
    fixture = {'synthetic':True,'archive':{
        'primarySourceIds':['synthetic-batch-00','synthetic-batch-01','synthetic-batch-02'],
        'relatedSkills':[{'skillId':'synthetic-universal-count','content':
            'SYNTHETIC disputed method: any observed counter difference is the exact requested-window total, even with gaps.'}],
        'consolidatedExperiences':[]}}
    for index in range(21):
        episode = {'fixture':'SYNTHETIC_NOT_BUSINESS_ACCEPTANCE','sourceIndex':index,
            'goal':'Read-only counter evidence; report precise scope and limitations',
            'condition': 'Complete requested window and matched endpoints' if index<20 else
                'The requested 300-second window has an 80-second observation gap; sampleCountLowerBound=120 is a lower bound only',
            'steps':['Read both boundary samples','Check coverage and reset','Retain the original receipts'],
            'acceptance':'Report matched-window count' if index<20 else
                'Do not report exact requested-window total or recovered service; preserve sampleCountLowerBound and the gap',
            'limitations':['Synthetic fixture, no actual service or successful task is claimed']}
        body = json.dumps(episode,ensure_ascii=False,sort_keys=True,separators=(',',':'))
        fixture['archive']['consolidatedExperiences'].append({'sourceId':f'synthetic-batch-{index:02d}',
            'sourceHash':hashlib.sha256(body.encode()).hexdigest(),'acceptedTaskEpisode':body})
    output.with_suffix('.fixture.json').write_text(json.dumps(fixture,ensure_ascii=False,indent=2)+'\n')
    result = {'status':'FAIL','scope':'SYNTHETIC_SOURCE_REAL_TERRA_PROTOCOL_ONLY_NO_BUSINESS_PUBLICATION',
        'jarSha256':digest,'businessWrites':0}
    source = ROOT/'scripts/acceptance/SkillSourceBatchModelProbe.java'
    try:
        with tempfile.TemporaryDirectory(prefix='ops-skill-batch-model-') as temporary:
            root = Path(temporary)
            with zipfile.ZipFile(jar) as packed:
                for entry in packed.namelist():
                    if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
                        (root/Path(entry).name).write_bytes(packed.read(entry))
            cp = str(root/'*')+os.pathsep+str(root)
            compile_result = subprocess.run([str(JAVA_HOME/'bin/javac'),'-cp',cp,'-d',str(root),str(source)],
                text=True,capture_output=True,timeout=30)
            if compile_result.returncode:
                raise RuntimeError('Probe compile failed: '+compile_result.stderr[-2500:])
            env = dict(os.environ,OPS_BATCH_PROBE_BINDING=json.dumps(binding),OPS_BATCH_PROBE_API_KEY=key)
            completed = subprocess.run([str(JAVA_HOME/'bin/java'),'-Xmx256m','-cp',cp,
                'cn.lgs.orbisops.trigger.ops.skill.SkillSourceBatchModelProbe',str(ledger),str(stop).lower(),str(output)],
                input=json.dumps(fixture,ensure_ascii=False),env=env,text=True,capture_output=True,timeout=430)
            if output.exists():
                result.update(json.loads(output.read_text()))
            if completed.returncode and result.get('diagnostic') is None:
                # Do not expose raw provider exceptions.
                result['diagnostic']='PROBE_PROCESS_FAILURE';result['returnCode']=completed.returncode
    except Exception as failure:
        result['failureType']=type(failure).__name__
        result['diagnostic']=str(failure)[:2600] if str(failure).startswith('Probe compile failed:') else 'PROBE_PRECONDITION_OR_TIMEOUT'
    finally:
        result['probeSourceSha256']=hashlib.sha256(source.read_bytes()).hexdigest()
        if ledger.exists():result['ledgerSha256']=hashlib.sha256(ledger.read_bytes()).hexdigest()
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:result.get(k) for k in ('status','diagnostic','cacheHits','businessWrites')}))
    if result['status']=='FAIL':raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--ledger',type=Path,required=True,help='Persistent synthetic review journal; retain it for resume')
    parser.add_argument('--stop-after-first-page',action='store_true')
    args=parser.parse_args();run(args.output,args.ledger,args.stop_after_first_page)
