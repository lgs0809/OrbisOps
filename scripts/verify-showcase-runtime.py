#!/usr/bin/env python3
"""Read-only runtime audit plus expected-denied DML in only own synthetic DBs."""
from pathlib import Path
import argparse
import importlib.util
import json
import subprocess
import datetime as dt

ROOT = Path(__file__).resolve().parents[1]
def load():
    spec=importlib.util.spec_from_file_location('showcase_workspace',ROOT/'scripts/showcase-workspace.py')
    h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h);return h

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if args.output.exists():raise ValueError('Preserve prior evidence')
    h=load();container=h.labels('mysql');h.labels('backend');h.labels('web')
    if h.sql('SELECT DATABASE();').strip()!=h.DATABASE:raise ValueError('Wrong database')
    env=dict(line.split('=',1) for line in h.ENV.read_text().splitlines() if '=' in line)
    days=len(h.history_dates())
    expected=[v*days for v in (90,72,78,6,6)]
    checks=[]
    for key in ('orders','payments','fulfilment'):
        schema='showcase_'+key+'_business';user='showcase_'+key+'_ro';pw=env['SHOWCASE_'+key.upper()+'_RO_PASSWORD']
        def execute(statement):
            shell='IFS= read -r task_sql_password; export MYSQL_PWD="$task_sql_password"; unset task_sql_password; exec mysql --default-character-set=utf8mb4 --raw -N -B -u'+user+' '+schema
            return subprocess.run(['docker','exec','-i',container,'sh','-c',shell],input=pw+'\n'+statement,text=True,capture_output=True)
        selected=execute("SELECT COUNT(*),COUNT(DISTINCT request_key),SUM(outcome='OK'),SUM(outcome='TIMEOUT'),SUM(outcome='REJECTED') FROM request_observation;")
        if selected.returncode or selected.stdout.strip()!='\t'.join(str(v) for v in expected):raise ValueError('Read-only sample count mismatch: '+key)
        grants=execute('SHOW GRANTS;')
        if grants.returncode or any(' ALL ' in line or 'INSERT' in line or 'UPDATE' in line or 'DELETE' in line for line in grants.stdout.splitlines()):raise ValueError('Unexpected writable grant: '+key)
        denied=execute("UPDATE request_observation SET duration_ms=duration_ms WHERE observation_id='nonexistent-showcase-denial-check';")
        if denied.returncode==0 or 'ERROR 1142' not in denied.stderr:raise ValueError('Expected DML denial did not occur: '+key)
        cross=execute('SELECT COUNT(*) FROM orbisops_showcase.admin_user;')
        if cross.returncode==0 or not any(code in cross.stderr for code in ('ERROR 1142','ERROR 1143')):raise ValueError('Read-only user unexpectedly accesses application identity store: '+key)
        checks.append({'schema':schema,'selectRows':expected[0],'distinctRequests':expected[1],'ok':expected[2],'timeout':expected[3],'rejected':expected[4],'grants':grants.stdout.splitlines(),'dmlRejected':True,'dmlErrorCode':1142,'identityDatabaseDenied':True})
    consumption=h.sql("SELECT JSON_OBJECT('activeRunLeases',(SELECT COUNT(*) FROM ai_ops_agent_run WHERE run_id LIKE 'showcase-run-%' AND (status NOT IN ('SUCCEEDED','FAILED','CANCELED') OR lease_token<>'' OR lease_expires_at IS NOT NULL)), 'unassignedTurns',(SELECT COUNT(*) FROM ai_ops_task_episode_turn WHERE source_run_ref LIKE 'showcase-run-%' AND status<>'ASSIGNED'), 'consumableEpisodeJobs',(SELECT COUNT(*) FROM ai_ops_episode_consolidation_job WHERE trigger_reasons='SHOWCASE_HISTORY' AND status NOT IN ('SOURCE_BLOCKED','SUCCEEDED')), 'pendingSkillJobs',(SELECT COUNT(*) FROM ai_ops_skill_evolution_job WHERE trigger_reason='SHOWCASE_HISTORY' AND status NOT IN ('COMPLETED','FAILED','SKIPPED','NO_CHANGE')), 'landingOperations',(SELECT COUNT(*) FROM ai_ops_change_package_landing_operation_run));")
    guards=json.loads(consumption.strip())
    if any(value!=0 for value in guards.values()):raise ValueError('Synthetic content became execution-eligible')
    token=h.login();projects=h.api('/api/v1/admin/ops-projects/snapshot',token=token)['projects']
    configuration=[]
    for project in projects:
        if not project['projectId'].startswith('daily-commerce-'):continue
        configuration.append({'projectId':project['projectId'],'scannedResources':sum(r['status']=='SCANNED' and r['schema'].get('source')=='live' for r in project['resources']),'mcps':len(project['generatedMcps']),'skills':len(project['projectSkills']),'knowledgeBases':len(project['projectKnowledgeBases'])})
    expected_projects={p['projectId'] for p in json.loads(h.CATALOG.read_text())['projects']}
    if {p['projectId'] for p in configuration}!=expected_projects or any((p['scannedResources'],p['mcps'],p['skills'],p['knowledgeBases'])!=(1,1,2,1) for p in configuration):raise ValueError('Incomplete or unscanned showcase configuration')
    bindings={service:h.command(['docker','inspect',h.labels(service),'--format','{{.Image}}']).strip() for service in ('backend','web')}
    report={'status':'PASS_LOCAL_READONLY_CONFIGURATION_AND_SYNTHETIC_SAFETY_ONLY','at':dt.datetime.now(dt.timezone.utc).isoformat(),'secretTransport':'container stdin; host argv excludes password','modelExecutionVerified':False,'syntheticHistoryIsAcceptanceEvidence':False,'businessChecks':checks,'executionEligibility':guards,'configuration':configuration,'images':bindings}
    h.private_write(args.output,json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
