#!/usr/bin/env python3
"""Save existing isolated target facts without sending a business request or modifying state."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def inspect(project, service, environment, output):
    if output.exists():
        raise ValueError('Retain prior evidence; choose a fresh output')
    if not project or not service or '/' in service:
        raise ValueError('Exact project/service identity required')
    resource = 'service://' + service + '/' + environment
    container = 'orbisops-acceptance-platform-closure-' + environment + '-1'
    reader = """import sqlite3,json,sys
with sqlite3.connect('file:/state/resource.sqlite?mode=ro',uri=True) as db:
 db.row_factory=sqlite3.Row
 key,project=sys.argv[1:3]
 state=[dict(r) for r in db.execute('SELECT * FROM resource WHERE resource_key=? AND project_id=?',(key,project))]
 if len(state)!=1:raise ValueError('Requested resource identity missing or ambiguous')
 records={t:[dict(r) for r in db.execute('SELECT * FROM '+t+' WHERE resource_key=? ORDER BY rowid',(key,))] for t in ['receipt','request','dispatch','audit_request','command_evidence']}
 print(json.dumps({'resource':state[0],**records}))
"""
    raw = subprocess.check_output(['docker','exec','-i',container,'python3','-',resource,project],input=reader,text=True)
    data = json.loads(raw)
    configuration = json.loads(data['resource']['configuration_json'])
    result = {'recordedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'projectId':project,
        'service':service,'environment':environment,'resourceKey':resource,
        'collectorSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'resourceFacts':data,'configuration':configuration,
        'configurationDigest':hashlib.sha256(json.dumps(configuration,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
        'counts':{k:len(v) for k,v in data.items() if isinstance(v,list)},
        'newExecutions':0,'newRequests':0,'boundary':'Native read-only SQLite facts; no inferred business acceptance, approval or model success.'}
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:result[k] for k in ['recordedAt','resourceKey','configuration','counts','newExecutions']}))
    return result

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--project',required=True);p.add_argument('--service',required=True)
    p.add_argument('--environment',choices=['test','prod'],required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();inspect(a.project,a.service,a.environment,a.output)
