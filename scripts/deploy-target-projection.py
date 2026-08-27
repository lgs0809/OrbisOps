#!/usr/bin/env python3
"""Upgrade owned target sidecars while retaining immutable event history and business configuration.

Actual traffic may continue. New events and monotonic destination acknowledgements are allowed;
old event deletion or modification, receipt/configuration changes and volume changes are rejected.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = '''import hashlib,json,sqlite3,sys
conn=sqlite3.connect('file:/state/target.sqlite?mode=ro',uri=True)
maximum=conn.execute('SELECT COALESCE(MAX(rowid),0) FROM events').fetchone()[0]
cutoff=int(sys.argv[1]) if len(sys.argv)>1 else maximum
h=hashlib.sha256()
count=0
for row in conn.execute('SELECT rowid,id,event_json FROM events WHERE rowid<=? ORDER BY rowid',(cutoff,)):
 h.update(json.dumps(row,ensure_ascii=False,separators=(',',':')).encode()+b'\\n');count+=1
current=conn.execute('SELECT COUNT(*),SUM(mysql_done),SUM(es_done) FROM events').fetchone()
print(json.dumps(dict(maximumRowid=maximum,cutoff=cutoff,retainedCount=count,immutableEventsSha256=h.hexdigest(),currentCounts=current)))
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.output.exists(): raise ValueError('Keep earlier evidence; choose a new output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    report=dict(status='NOT_COMPLETED',startedAt=datetime.now(timezone.utc).isoformat())
    def write():args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    def command(*args):return subprocess.check_output(args,text=True,cwd=ROOT)
    support=runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    compose=runpy.run_path(str(ROOT/'scripts/local-acceptance.py'))['compose']
    idle=runpy.run_path(str(ROOT/'scripts/deploy-tested-acceptance.py'))['require_idle_runs']
    def native(service,cutoff=None):
        container='orbisops-acceptance-'+service+'-1'
        details=json.loads(command('docker','inspect',container))[0]
        data=json.loads(command('docker','exec',container,'python3','-c',SNAPSHOT,*([str(cutoff)] if cutoff is not None else [])))
        return dict(image=details['Image'],volumes=sorted(m['Name'] for m in details['Mounts'] if m['Type']=='volume'),events=data)
    sql='\n'.join('SELECT * FROM '+db+'.'+table+';' for db in ['ops_acceptance_business_a','ops_acceptance_business_prepare'] for table in ['acceptance_service','ops08_deployment_receipt'])
    args.output.with_suffix('.sql').write_text(sql+'\n')
    try:
        test=subprocess.run(['python3','-m','unittest','discover','-s','deploy/acceptance','-p','test_event_projection.py','-v'],cwd=ROOT,capture_output=True,text=True)
        args.output.with_suffix('.tests.log').write_text(test.stdout+test.stderr)
        report['testsExitCode']=test.returncode;test.check_returncode()
        idle()
        services=['workflow-target','prepare-target']
        report['before']={s:native(s) for s in services}
        before_business=support['sql'](sql)
        report['businessBeforeSha256']=hashlib.sha256(before_business.encode()).hexdigest()
        write()
        compose('--profile','business','build','workflow-target').check_returncode()
        idle()
        compose('--profile','business','up','-d','--no-deps','--no-build',*services).check_returncode()
        deadline=time.monotonic()+60
        while True:
            health={s:command('docker','inspect','orbisops-acceptance-'+s+'-1','--format','{{.State.Health.Status}}').strip() for s in services}
            if all(v=='healthy' for v in health.values()):break
            if time.monotonic()>deadline:raise TimeoutError('Retained target startup deadline')
            time.sleep(1)
        report['after']={s:native(s,report['before'][s]['events']['cutoff']) for s in services}
        report['businessAfterSha256']=hashlib.sha256(support['sql'](sql).encode()).hexdigest()
        report['checks']={'unchangedBusinessConfigurationAndWriteReceipts':report['businessBeforeSha256']==report['businessAfterSha256']}
        for s in services:
            a,b=report['before'][s],report['after'][s]
            report['checks'][s+'OldEventsPreserved']=all(a['events'][k]==b['events'][k] for k in ['retainedCount','immutableEventsSha256'])
            report['checks'][s+'VolumesPreserved']=a['volumes']==b['volumes']
            for file,target in [('workflow-target.py','server.py'),('event_projection.py','event_projection.py')]:
                actual=command('docker','exec','orbisops-acceptance-'+s+'-1','sha256sum','/opt/workflow-target/'+target).split()[0]
                report['checks'][s+file+'ExactSource']=actual==hashlib.sha256((ROOT/'deploy/acceptance'/file).read_bytes()).hexdigest()
        assert all(report['checks'].values()),report['checks']
        report.update(status='PASS_RETAINED_TARGET_PROJECTION_DEPLOYMENT',finishedAt=datetime.now(timezone.utc).isoformat(),boundary='Deployment and unchanged historical events/configuration/receipts only; destination backlog recovery and business acceptance require independent readback.')
    except Exception as error:
        report.update(status='FAIL_TARGET_PROJECTION_DEPLOYMENT',error=type(error).__name__+': '+str(error));raise
    finally:write()
    print(json.dumps(dict(status=report['status'],checks=report['checks'])))

if __name__=='__main__':main()
