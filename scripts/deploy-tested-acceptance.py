#!/usr/bin/env python3
"""Deploy the locally tested JAR into the existing isolated acceptance stack, retaining all volumes.

Run Maven package first and supply its successful log. Initial image/dependencies
are prepared by local-acceptance.py up. This command never changes model credentials.
"""
from pathlib import Path
import argparse
import hashlib
import json
import runpy
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def require_idle_runs(recover_expired_jobs=False):
    # Durable approval waits have no running worker and survive restart via their checkpoint.
    query = """SELECT JSON_OBJECT(
        'activeRuns',(SELECT COUNT(*) FROM ai_ops_agent_run WHERE status IN ('QUEUED','RUNNING','CANCELLING')),
        'liveJobs',(SELECT COUNT(*) FROM ai_ops_skill_evolution_job j
           LEFT JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
           WHERE j.status='RUNNING' AND (s.job_id IS NULL OR s.lease_until_ms>UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)),
        'expiredJobs',(SELECT COALESCE(JSON_ARRAYAGG(j.job_id),JSON_ARRAY()) FROM ai_ops_skill_evolution_job j
           JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
           WHERE j.status='RUNNING' AND s.lease_until_ms<=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000));"""
    raw = subprocess.check_output([
        "docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B orbisops_acceptance'
    ], input=query, text=True)
    state = json.loads(raw)
    if state['activeRuns'] or state['liveJobs'] or (state['expiredJobs'] and not recover_expired_jobs):
        raise RuntimeError("Deployment postponed: active runs or owned background jobs must finish first")
    return state


def main(test_log, output, recover_expired_jobs=False, test_summary=None):
    if output.exists():
        raise ValueError("Choose a new evidence filename; previous deployment evidence is retained")
    log = test_log.read_text()
    if "BUILD SUCCESS" not in log or "Tests run:" not in log or "Tests are skipped" in log:
        raise ValueError("A successful Maven package log with executed tests is required")
    jar = ROOT / "server/orbisops-app/target/orbisops-app.jar"
    if jar.stat().st_mtime > test_log.stat().st_mtime:
        raise ValueError("The JAR is newer than the supplied test log; rerun the appropriate checks")
    before = hashlib.sha256(jar.read_bytes()).hexdigest()
    verified_tests = None
    if test_summary is not None:
        verified_tests = json.loads(test_summary.read_text())
        checks = verified_tests.get('checks', {})
        totals = verified_tests.get('totals', {})
        ordinary = next((stage for stage in verified_tests.get('stages', [])
                         if stage.get('stage') == 'ordinary'), {})
        if verified_tests.get('status') != 'PASS' or not checks or not all(checks.values()) \
                or totals.get('tests', 0) <= 0 or totals.get('failures') != 0 or totals.get('errors') != 0 \
                or verified_tests.get('jarSha256') != before or not verified_tests.get('sourceHash') \
                or ordinary.get('logSha256') != hashlib.sha256(test_log.read_bytes()).hexdigest():
            raise ValueError('Fresh complete test summary, ordinary log and exact JAR must agree')
    recovery_state = require_idle_runs(recover_expired_jobs)
    compose = runpy.run_path(str(ROOT / "scripts/local-acceptance.py"))["compose"]
    subprocess.run(["docker", "build", "-f", "deploy/acceptance/tested-server.Dockerfile", "-t",
                    "orbisops/server:2.0.0-acceptance", "."], cwd=ROOT, check=True)
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == before
    recovery_state = require_idle_runs(recover_expired_jobs)
    lifecycle = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))
    with lifecycle['suspended_peers']() as namespace_recovery:
        compose("up", "-d", "--no-build", "backend")
        deadline = time.monotonic() + 120
        while True:
            try:
                with urllib.request.urlopen("http://127.0.0.1:18089/api/v1/setup/status", timeout=3) as response:
                    assert response.status == 200
                break
            except (OSError, AssertionError):
                if time.monotonic() > deadline:
                    raise
                time.sleep(2)
    def inspect(name, field):
        return subprocess.check_output(["docker", "inspect", name, "--format", "{{." + field + "}}"], text=True).strip()
    deployed = subprocess.check_output(["docker", "exec", "orbisops-acceptance-backend-1", "sha256sum",
                                        "/opt/orbisops/orbisops.jar"], text=True).split()[0]
    assert before == deployed
    image_id=inspect("orbisops-acceptance-backend-1", "Image")
    image_info=json.loads(subprocess.check_output(["docker", "image", "inspect", image_id],text=True))[0]
    result = {"recoveryAdmission": recovery_state, "recoverExpiredJobs": recover_expired_jobs,
              "namespaceRecovery": namespace_recovery,
              "backendImage": image_id,
              "backendImageLayers": len(image_info['RootFS']['Layers']), "backendImageSizeBytes": image_info['Size'],
              "webImage": inspect("orbisops-acceptance-web-1", "Image"), "jarSha256": before,
              "containerJar": deployed, "testLogSha256": hashlib.sha256(test_log.read_bytes()).hexdigest(),
              "migrationManifestSha256": hashlib.sha256((ROOT / "server/db/migrations/manifest.tsv").read_bytes()).hexdigest(),
              "backendStartedAt": inspect("orbisops-acceptance-backend-1", "State.StartedAt")}
    if verified_tests is not None:
        result.update(status='PASS', scope='TESTED_ARTIFACT_DEPLOYMENT_ONLY_NO_BUSINESS_ACCEPTANCE_CLAIM',
                      sourceHash=verified_tests['sourceHash'],
                      checks={'freshCompleteTestsPassed': True, 'exactTestedJarDeployed': deployed == before,
                              'peerImagesAndMountsPreserved': all(peer['imageAndMountsPreserved']
                                  for peer in namespace_recovery['peersAfter'])},
                      testSummary=str(test_summary),
                      testSummarySha256=hashlib.sha256(test_summary.read_bytes()).hexdigest(),
                      testTotals=verified_tests['totals'])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--test-summary", type=Path,
                        help="Fresh ordinary/native XML review; binds the complete tested source and exact JAR")
    parser.add_argument("--recover-expired-jobs", action="store_true",
                        help="Allow restart only for already expired Skill leases; no rows or outcomes are changed")
    args = parser.parse_args()
    main(args.test_log, args.output, args.recover_expired_jobs, args.test_summary)
