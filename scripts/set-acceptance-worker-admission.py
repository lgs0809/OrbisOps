#!/usr/bin/env python3
"""Change the existing startup worker flag only after owned executions are idle.

No queue rows, retry clocks, outcomes, credentials or permissions are changed.
The same tested image is restarted with all shared-namespace peers and data retained.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]
KEY = 'ORBISOPS_SKILL_EVOLVER_WORKER_ENABLED'


def main(enabled, output, reason):
    if output.exists():
        raise ValueError('Previous admission evidence is retained')
    h = runpy.run_path(str(ROOT/'scripts/test-mcp-runtime.py'))
    deployment = runpy.run_path(str(ROOT/'scripts/deploy-tested-acceptance.py'))
    lifecycle = runpy.run_path(str(ROOT/'scripts/backend-namespace-lifecycle.py'))
    compose = runpy.run_path(str(ROOT/'scripts/local-acceptance.py'))['compose']
    query = "SELECT JSON_OBJECT('jobId',job_id,'status',status,'attempts',attempts,'nextRunAt',next_run_at,'lastError',last_error) FROM ai_ops_skill_evolution_job ORDER BY job_id"

    def setting():
        item = json.loads(subprocess.check_output(['docker','inspect','orbisops-acceptance-backend-1'], text=True))[0]
        values = dict(v.split('=',1) for v in item['Config']['Env'] if '=' in v)
        return {'workerEnabled': values.get(KEY), 'image': item['Image'],
                'startedAt': item['State']['StartedAt'],
                'jarSha256': subprocess.check_output(['docker','exec','orbisops-acceptance-backend-1',
                    'sha256sum','/opt/orbisops/orbisops.jar'],text=True).split()[0]}

    report = {'status':'CHECKING_IDLE_ADMISSION','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
              'reason':reason,'requestedWorkerEnabled':enabled,'queueOrClockMutations':0,
              'before':setting(),'queueBefore':h['rows'](query)}
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    report['idleAdmission'] = deployment['require_idle_runs']()
    os.environ[KEY] = 'true' if enabled else 'false'
    with lifecycle['suspended_peers']() as recovery:
        report['namespaceRecovery'] = recovery
        report['secondIdleAdmission'] = deployment['require_idle_runs']()
        compose('up','-d','--no-deps','--no-build','backend')
        lifecycle['wait_backend']()
    report.update(after=setting(),queueAfter=h['rows'](query),finishedAt=dt.datetime.now(dt.timezone.utc).isoformat())
    before_jobs={row['jobId']:row for row in report['queueBefore']}
    after_jobs={row['jobId']:row for row in report['queueAfter']}
    unchanged = report['queueBefore'] == report['queueAfter']
    report['queueObservation'] = {
        'unchanged':unchanged,
        'existingRows':len(before_jobs), 'observedRowsAfter':len(after_jobs),
        'changedExistingJobs':[key for key in before_jobs if key in after_jobs and before_jobs[key]!=after_jobs[key]],
        'newlyAdmittedJobs':sorted(set(after_jobs)-set(before_jobs)),
        'boundary':'Only the existing startup flag changed. Enabled native workers may progress or admit jobs before the post-start observation; those actual changes are retained, never described as unchanged.'}
    queue_check=unchanged if not enabled else (set(before_jobs)<=set(after_jobs) and all(
        after_jobs[key]['attempts']>=row['attempts'] for key,row in before_jobs.items()))
    report['checks'] = {
        'requestedExistingSettingApplied': report['after']['workerEnabled'] == os.environ[KEY],
        'sameImageAndJar': all(report['before'][key] == report['after'][key] for key in ('image','jarSha256')),
        'pausedQueuePreservedOrEnabledNativeProgressRetained': queue_check,
        'peersAndDataPreserved': all(peer['imageAndMountsPreserved'] for peer in recovery['peersAfter'])}
    report['status'] = 'PASS_EXISTING_WORKER_SETTING' if all(report['checks'].values()) else 'FAIL'
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    output.with_suffix('.sql').write_text(query+';\n')
    print(json.dumps({key:report[key] for key in ('status','checks','before','after')}))
    if report['status'] == 'FAIL': raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--enabled', choices=['true','false'], required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--reason',required=True)
    args = parser.parse_args()
    main(args.enabled == 'true',args.output,args.reason)
