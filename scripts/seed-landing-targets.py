#!/usr/bin/env python3
"""Initialize linked preparation data and local target control credentials without changing business versions."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ENV = ROOT / "deploy/.env.acceptance"


def initialize():
    values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))
    keys = ("OPS_ACCEPTANCE_PREPARE_DB_PASSWORD", "OPS_ACCEPTANCE_PREPARE_CONTROL_TOKEN",
            "OPS_ACCEPTANCE_PROD_CONTROL_TOKEN", "OPS_ACCEPTANCE_LANDING_MCP_TOKEN")
    additions = {key: secrets.token_urlsafe(36) for key in keys if not values.get(key)}
    with ENV.open("a") as stream:
        stream.write("".join("\n" + key + "=" + value for key, value in additions.items()) + ("\n" if additions else ""))
    os.chmod(ENV, 0o600)
    values.update(additions)
    password = values[keys[0]]
    if not re.fullmatch(r"[A-Za-z0-9_-]{30,100}", password):
        raise ValueError("Existing generated credential has unexpected format; preserved")
    sql = (ROOT / "scripts/fixtures/ops08-target-init.sql").read_text().replace("{{PREPARE_PASSWORD}}", password)
    query = """SELECT JSON_OBJECT('environment','test','services',(SELECT COUNT(*) FROM acceptance_service),
        'linkedOrders',(SELECT COUNT(*) FROM acceptance_order o JOIN acceptance_customer c USING(customer_id)
        JOIN acceptance_service s USING(service_id)), 'receipts',(SELECT COUNT(*) FROM ops08_deployment_receipt))
        FROM dual;"""
    result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw'],
        input=sql + "\nUSE ops_acceptance_business_prepare;\n" + query,
        text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("Landing target initialization failed; private SQL errors were not printed")
    print(json.dumps({"status": "INITIALIZED", "counts": json.loads(result.stdout.strip()),
                      "existingDataPreserved": True, "changedApprovalsOrBusinessVersions": False}))


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__).parse_args()
    initialize()
