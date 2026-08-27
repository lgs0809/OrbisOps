#!/usr/bin/env python3
"""Prove baseline refuses an existing business table in a dedicated regression schema."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
values = dict(line.split("=", 1) for line in (ROOT / "deploy/.env.acceptance").read_text().splitlines() if line and not line.startswith("#"))
database = "orbisops_migration_guard_regression"
base = ["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B']
subprocess.run(base, input=f"CREATE DATABASE IF NOT EXISTS {database}; CREATE TABLE IF NOT EXISTS {database}.preserved_business_row(id INT PRIMARY KEY, value VARCHAR(30)); INSERT INTO {database}.preserved_business_row VALUES (1,'keep-existing-data') ON DUPLICATE KEY UPDATE id=id;", text=True, check=True)
env = dict(os.environ, MYSQL_DATABASE=database, MYSQL_USERNAME="root", MYSQL_PWD=values["ORBISOPS_MYSQL_ROOT_PASSWORD"])
result = subprocess.run(["docker", "compose", "-p", "orbisops-acceptance", "--env-file", "deploy/.env.acceptance", "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml", "run", "--rm", "--no-deps", "-e", "MYSQL_DATABASE", "-e", "MYSQL_USERNAME", "-e", "MYSQL_PWD", "migrate"], cwd=ROOT, env=env, capture_output=True, text=True)
if result.returncode != 6 or "refusing baseline migration" not in result.stderr:
    raise RuntimeError("Non-empty baseline guard did not reject as expected")
preserved = subprocess.run(base, input=f"SELECT value FROM {database}.preserved_business_row WHERE id=1; SELECT COUNT(*) FROM {database}.orbisops_schema_history WHERE version='001';", text=True, capture_output=True, check=True).stdout.strip()
if preserved != "keep-existing-data\n0":
    raise RuntimeError("Guard changed pre-existing business data or recorded a false baseline")
print("PASS: baseline refused with exit 6; existing business row retained; migration 001 was not recorded.")
