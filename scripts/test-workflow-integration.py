#!/usr/bin/env python3
"""Run OPS-02 against a separate regression schema in the acceptance MySQL container.

Never resets a database. A fresh isolated schema prevents older lease candidates from crowding the bounded recovery query; every test also uses a synthetic run prefix.
Credentials are passed through the child environment, not command-line values.
"""
from pathlib import Path
import argparse
import os
import subprocess
import datetime as dt
import re
import uuid

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--tests", choices=("WorkflowMySqlTest", "WorkflowToolBudgetMySqlTest", "all"), default="WorkflowMySqlTest")
parser.add_argument("--database", default="orbisops_workflow_regression_" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d") + "_" + uuid.uuid4().hex[:8])
parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
args = parser.parse_args()
if not re.fullmatch(r"orbisops_workflow_regression(?:_[a-z0-9_]+)?", args.database):
    parser.error("Only an explicitly isolated workflow regression database is supported")
values = dict(line.split("=", 1) for line in (ROOT / "deploy/.env.acceptance").read_text().splitlines()
              if line and not line.startswith("#"))
env = dict(os.environ)
if args.java_home:
    env.update(JAVA_HOME=args.java_home, PATH=args.java_home + "/bin:" + env["PATH"])
env["MAVEN_OPTS"] = "-Xmx512m -XX:ActiveProcessorCount=2"
print("Workflow regression database: " + args.database, flush=True)
env.update({"MYSQL_DATABASE": args.database, "MYSQL_USERNAME": "root",
            "MYSQL_PWD": values["ORBISOPS_MYSQL_ROOT_PASSWORD"]})
compose = ["docker", "compose", "-p", "orbisops-acceptance", "--env-file", "deploy/.env.acceptance",
           "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml"]
subprocess.run(compose + ["run", "--rm", "--no-deps",
                "-e", "MYSQL_DATABASE", "-e", "MYSQL_USERNAME", "-e", "MYSQL_PWD",
                "-v", str(ROOT / "server/db/migrations") + ":/opt/orbisops/db/migrations:ro",
                "-v", str(ROOT / "server/scripts/db-migrate.sh") + ":/opt/orbisops/scripts/db-migrate.sh:ro", "migrate"],
               cwd=ROOT, env=env, check=True)
user = values["ORBISOPS_MYSQL_USERNAME"]
if not user.replace("_", "").isalnum():
    raise ValueError("Unexpected acceptance database user")
subprocess.run(compose + ["exec", "-T", "mysql", "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],
               cwd=ROOT, input=f"GRANT ALL ON `{args.database}`.* TO '{user}'@'%';\n", text=True, check=True)
env.update({"ORBISOPS_WORKFLOW_TEST_URL": "jdbc:mysql://127.0.0.1:" + values["ORBISOPS_MYSQL_PORT"]
            + "/" + args.database + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
            "ORBISOPS_WORKFLOW_TEST_USER": user, "ORBISOPS_WORKFLOW_TEST_PASSWORD": values["ORBISOPS_MYSQL_PASSWORD"]})
env.update({"ORBISOPS_WORKFLOW_TEST_ADMIN_USER": "root",
            "ORBISOPS_WORKFLOW_TEST_ADMIN_PASSWORD": values["ORBISOPS_MYSQL_ROOT_PASSWORD"]})
# The host's Maven proxy configuration may omit numeric loopback addresses.
# Keep this override local to the test JVM; never send local database traffic to a proxy.
subprocess.run(["mvn", "-B", "-DsocksNonProxyHosts=localhost|127.*|[::1]", "-pl", "orbisops-app", "-am", "-Dtest=" + ("WorkflowMySqlTest,WorkflowToolBudgetMySqlTest" if args.tests == "all" else args.tests),
                "-Dsurefire.failIfNoSpecifiedTests=false", "-DargLine=-Xmx512m -XX:ActiveProcessorCount=2", "test"], cwd=ROOT / "server", env=env, check=True)
