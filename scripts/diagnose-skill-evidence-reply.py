#!/usr/bin/env python3
"""One real Terra directory request, without changing a job or accepting its answer as an experience.

The raw reply is retained only in the ignored, owner-readable private acceptance directory.
Public evidence contains hashes and protocol metadata, never the provider credential or raw reply.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import textwrap
import time
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main(output, follow=False):
    if output.exists():
        raise ValueError('Choose a new filename')
    support = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    binding = support['rows']("SELECT JSON_OBJECT('base',a.base_url,'path',a.completions_path,'key',a.api_key) "
        "FROM ai_client_api a JOIN ai_client_model m ON a.api_id=m.api_id "
        "WHERE m.model_name='gpt-5.6-terra' AND a.status=1 AND m.status=1")
    assert len(binding) == 1
    def resolve(value):
        return re.sub(r'\$\{env:([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}',
                      lambda m: support['values'].get(m[1]) or os.environ.get(m[1]) or m[2] or '', value)
    bound = {k: resolve(v) for k, v in binding[0].items()}
    assert bound['key'] and not bound['key'].startswith('${')
    rows = support['rows']("SELECT JSON_OBJECT('body',input_json) FROM ai_ops_skill_evolution_source "
        "WHERE project_id='ops-acceptance-a' AND source_id='task-acceptance-d2235b30-a725-4586-aa23-2fee201cc67c'")
    assert len(rows) == 1
    with zipfile.ZipFile(ROOT/'server/orbisops-app/target/orbisops-app.jar') as jar:
        slf = next(Path(n).name for n in jar.namelist() if n.startswith('BOOT-INF/lib/slf4j-api-'))
        json_jars=[Path(n).name for n in jar.namelist() if n.startswith('BOOT-INF/lib/fastjson')]
    logging = next((Path.home()/'.m2/repository/org/slf4j/slf4j-api').glob('*/'+slf))
    cp = os.pathsep.join([str(ROOT/'server'/module/'target/classes') for module in ('orbisops-domain','orbisops-trigger')]+[str(logging)])
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    wire = json.loads(subprocess.run([java,'--class-path',cp,str(ROOT/'scripts/InspectSkillModelInput.java'),'--directory-request'],
        input=rows[0]['body'],capture_output=True,text=True,check=True,timeout=30).stdout)
    code=(ROOT/'server/orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/skill/OpsSkillExperienceGroupingModelAdapter.java').read_text()
    prompt=textwrap.dedent(code.split('model.generate("""',1)[1].split('""",accepted)',1)[0]).lstrip('\n')
    if follow:
        for name in json_jars:
            cp += os.pathsep+str(next((Path.home()/'.m2/repository').rglob(name)))
        return follow_protocol(output,bound,prompt,java,cp,rows[0]['body'])
    payload={'model':'gpt-5.6-terra','response_format':{'type':'json_object'},'stream':False,
        'messages':[{'role':'system','content':prompt+'\n'+wire['instruction']},{'role':'user','content':wire['input']}]}
    request=urllib.request.Request(bound['base'].rstrip('/')+'/'+bound['path'].lstrip('/'),
        json.dumps(payload,ensure_ascii=False).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+bound['key']})
    started=time.monotonic()
    with urllib.request.urlopen(request,timeout=120) as response:
        body=response.read(); data=json.loads(body)
    private=ROOT/'deploy/.acceptance-private'/('skill-reply-'+uuid.uuid4().hex+'.json')
    fd=os.open(private,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as file: file.write(body)
    text=data['choices'][0]['message'].get('content') or ''
    try: parsed=json.loads(text); valid=isinstance(parsed,dict)
    except ValueError: parsed={};valid=False
    result={'status':'JSON_OBJECT_RETURNED' if valid else 'NON_JSON_REPLY','scope':'ONE_REAL_DIAGNOSTIC_CALL_NO_JOB_RESULT',
        'responseModel':data.get('model'),'finishReason':data['choices'][0].get('finish_reason'),
        'inputHash':hashlib.sha256(wire['input'].encode()).hexdigest(),'replyHash':hashlib.sha256(text.encode()).hexdigest(),
        'replyChars':len(text),'seconds':round(time.monotonic()-started,3),'replyKeys':list(parsed) if valid else [],
        'privateReplyPath':str(private),'sourceExperienceSaved':False}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


def follow_protocol(output, bound, prompt, java, cp, source):
    process=subprocess.Popen([java,'--class-path',cp,str(ROOT/'scripts/DiagnoseSkillEvidenceProtocol.java')],
        stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    result={'status':'RUNNING','scope':'REAL_PROTOCOL_DIAGNOSTIC_NO_JOB_WRITES','sourceExperienceSaved':False,'calls':[]}
    started=time.monotonic()
    try:
        process.stdin.write(json.dumps({'source':source},ensure_ascii=False)+'\n');process.stdin.flush()
        for round_number in range(7):
            line=process.stdout.readline()
            if not line: raise RuntimeError('DIAGNOSTIC_PROTOCOL_PROCESS_EXITED')
            step=json.loads(line)
            if step['type']!='MODEL_REQUEST':
                result['status']='PROTOCOL_COMPLETED_READ_ONLY' if step['type']=='FINAL' else step.get('code','PROTOCOL_FAILED')
                break
            remaining=120-(time.monotonic()-started)
            if remaining<=0: raise TimeoutError('DIAGNOSTIC_TOTAL_BUDGET')
            payload={'model':'gpt-5.6-terra','response_format':{'type':'json_object'},'stream':False,
                'messages':[{'role':'system','content':prompt+'\n'+step['instruction']},{'role':'user','content':step['input']}]}
            req=urllib.request.Request(bound['base'].rstrip('/')+'/'+bound['path'].lstrip('/'),
                json.dumps(payload,ensure_ascii=False).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+bound['key']})
            with urllib.request.urlopen(req,timeout=remaining) as response: body=response.read();data=json.loads(body)
            assert data.get('model')=='gpt-5.6-terra'
            private=ROOT/'deploy/.acceptance-private'/('skill-reply-'+uuid.uuid4().hex+'.json')
            with os.fdopen(os.open(private,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'wb') as file: file.write(body)
            reply=data['choices'][0]['message'].get('content') or ''
            result['calls'].append({'round':round_number,'replyChars':len(reply),'privateReplyPath':str(private),
                'inputHash':hashlib.sha256(step['input'].encode()).hexdigest(),'replyHash':hashlib.sha256(reply.encode()).hexdigest()})
            process.stdin.write(json.dumps({'reply':reply},ensure_ascii=False)+'\n');process.stdin.flush()
        else: result['status']='DIAGNOSTIC_ROUND_LIMIT'
    except Exception as error:
        result.update(status='DIAGNOSTIC_FAILED',failureType=type(error).__name__)
    finally:
        process.terminate();process.wait(timeout=10)
        result['seconds']=round(time.monotonic()-started,3)
        output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--follow',action='store_true',help='Follow the bounded directory protocol; still no job writes')
    args=parser.parse_args();main(args.output,args.follow)
