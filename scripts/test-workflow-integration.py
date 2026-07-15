#!/usr/bin/env python3
"""Run OPS-02 against a separate regression schema in the acceptance MySQL container.

Never resets a database. Each test uses a new, explicitly synthetic session prefix.
Credentials are passed through the child environment, not command-line values.
"""
from pathlib import Path
import argparse
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--tests", choices=("WorkflowMySqlTest", "WorkflowToolBudgetMySqlTest"), default="WorkflowMySqlTest")
args = parser.parse_args()
values = dict(line.split("=", 1) for line in (ROOT / "deploy/.env.acceptance").read_text().splitlines()
              if line and not line.startswith("#"))
env = dict(os.environ)
env.update({"MYSQL_DATABASE": "orbisops_workflow_regression", "MYSQL_USERNAME": "root",
            "MYSQL_PWD": values["ORBISOPS_MYSQL_ROOT_PASSWORD"]})
subprocess.run(["docker", "compose", "-p", "orbisops-acceptance", "--env-file", "deploy/.env.acceptance",
                "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml", "run", "--rm", "--no-deps",
                "-e", "MYSQL_DATABASE", "-e", "MYSQL_USERNAME", "-e", "MYSQL_PWD",
                "-v", str(ROOT / "server/db/migrations") + ":/opt/orbisops/db/migrations:ro",
                "-v", str(ROOT / "server/scripts/db-migrate.sh") + ":/opt/orbisops/scripts/db-migrate.sh:ro", "migrate"],
               cwd=ROOT, env=env, check=True)
user = values["ORBISOPS_MYSQL_USERNAME"]
if not user.replace("_", "").isalnum():
    raise ValueError("Unexpected acceptance database user")
subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],
               input=f"GRANT ALL ON orbisops_workflow_regression.* TO '{user}'@'%';\n", text=True, check=True)
env.update({"ORBISOPS_WORKFLOW_TEST_URL": "jdbc:mysql://127.0.0.1:" + values["ORBISOPS_MYSQL_PORT"]
            + "/orbisops_workflow_regression?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
            "ORBISOPS_WORKFLOW_TEST_USER": user, "ORBISOPS_WORKFLOW_TEST_PASSWORD": values["ORBISOPS_MYSQL_PASSWORD"]})
# The host's Maven proxy configuration may omit numeric loopback addresses.
# Keep this override local to the test JVM; never send local database traffic to a proxy.
subprocess.run(["mvn", "-B", "-DsocksNonProxyHosts=localhost|127.*|[::1]", "-pl", "orbisops-app", "-am", "-Dtest=" + args.tests,
                "-Dsurefire.failIfNoSpecifiedTests=false", "test"], cwd=ROOT / "server", env=env, check=True)
