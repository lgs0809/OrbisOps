#!/usr/bin/env python3
"""Own synthetic business schemas; only dedicated showcase container, INSERT-only data."""
import datetime as dt
import json
import re

SOURCE = "synthetic-demonstration:orbisops-daily-workspace-v2"
TABLES = {
    "seed_metadata": "seed_id VARCHAR(100) PRIMARY KEY, source VARCHAR(160) NOT NULL",
    "service_inventory": "service_id VARCHAR(100) PRIMARY KEY, name VARCHAR(150) NOT NULL, purpose VARCHAR(300) NOT NULL, environment VARCHAR(24) NOT NULL, source VARCHAR(160) NOT NULL",
    "request_observation": "observation_id VARCHAR(100) PRIMARY KEY, service_id VARCHAR(100) NOT NULL, request_key VARCHAR(100) NOT NULL, occurred_at DATETIME NOT NULL, response_code INT NOT NULL, duration_ms INT NOT NULL, outcome VARCHAR(24) NOT NULL, source VARCHAR(160) NOT NULL, KEY idx_request_window(service_id,occurred_at), FOREIGN KEY(service_id) REFERENCES service_inventory(service_id)",
    "service_dependency": "dependency_id VARCHAR(100) PRIMARY KEY, service_id VARCHAR(100) NOT NULL, dependency_name VARCHAR(100) NOT NULL, relationship VARCHAR(40) NOT NULL, source VARCHAR(160) NOT NULL, FOREIGN KEY(service_id) REFERENCES service_inventory(service_id)",
    "service_configuration": "configuration_id VARCHAR(100) PRIMARY KEY, service_id VARCHAR(100) NOT NULL, revision INT NOT NULL, configuration_json TEXT NOT NULL, source VARCHAR(160) NOT NULL, FOREIGN KEY(service_id) REFERENCES service_inventory(service_id)",
    "change_window": "window_id VARCHAR(100) PRIMARY KEY, service_id VARCHAR(100) NOT NULL, starts_at DATETIME NOT NULL, ends_at DATETIME NOT NULL, purpose VARCHAR(300) NOT NULL, status VARCHAR(24) NOT NULL, source VARCHAR(160) NOT NULL, FOREIGN KEY(service_id) REFERENCES service_inventory(service_id)"
}

def dataset(project, dates):
    schema = "showcase_" + project["key"] + "_business"
    rows = []
    def add(table, key, fields):
        rows.append({"table": schema + "." + table, "key": key, "fields": fields})
    add("seed_metadata", {"seed_id": "daily-workspace-v2"}, {"seed_id": "daily-workspace-v2", "source": SOURCE})
    for service in project["services"]:
        sid = service["serviceId"]
        add("service_inventory", {"service_id": sid}, {"service_id": sid, "name": service["name"], "purpose": service["purpose"], "environment": "test", "source": SOURCE})
        did = sid + "-store"
        add("service_dependency", {"dependency_id": did}, {"dependency_id": did, "service_id": sid, "dependency_name": service["dependency"], "relationship": "READ_WRITE_DATABASE", "source": SOURCE})
        cid = sid + "-baseline"
        add("service_configuration", {"configuration_id": cid}, {"configuration_id": cid, "service_id": sid, "revision": 1, "configuration_json": {"requestTimeoutMs": 3000, "idempotencyRequired": True, "environment": "test", "syntheticDemonstration": True}, "source": SOURCE})
        for n in range(2):
            wid = sid + "-window-" + str(n + 1)
            add("change_window", {"window_id": wid}, {"window_id": wid, "service_id": sid, "starts_at": dates[n * 28].isoformat() + " 02:00:00", "ends_at": dates[n * 28].isoformat() + " 03:00:00", "purpose": "计划维护窗口，仅描述业务约束，未执行变更", "status": "ARCHIVED", "source": SOURCE})
    for day in dates:
        for n in range(1, 91):
            service = project["services"][(n - 1) % 3]
            oid = project["key"] + "-observation-" + day.strftime("%Y%m%d") + f"-{n:03}"
            outcome = "TIMEOUT" if n % 15 == 0 else "REJECTED" if n % 10 == 0 else "OK"
            date = day
            add("request_observation", {"observation_id": oid}, {"observation_id": oid, "service_id": service["serviceId"], "request_key": project["key"] + "-request-" + day.strftime("%Y%m%d") + f"-{((n-1)%72)+1:03}", "occurred_at": date.isoformat() + f" 00:{n%20:02}:00", "response_code": 504 if outcome == "TIMEOUT" else 409 if outcome == "REJECTED" else 200, "duration_ms": 5000 if outcome == "TIMEOUT" else 80 if outcome == "REJECTED" else 50+n*37%900, "outcome": outcome, "source": SOURCE})
    return rows


def run(h, history, apply=False):
    if h["sql"]("SELECT DATABASE();").strip() != "orbisops_showcase":
        raise ValueError("Business presentation must use only own showcase database container")
    spec = json.loads(h["CATALOG"].read_text())
    rows = []
    namespace_statements = []
    secret_values = dict(line.split("=", 1) for line in h["ENV"].read_text().splitlines() if "=" in line)
    for project in spec["projects"]:
        key = project["key"]
        schema = "showcase_" + key + "_business"
        username = "showcase_" + key + "_ro"
        if not re.fullmatch(r"showcase_(orders|payments|fulfilment)_business", schema):
            raise ValueError("Invalid presentation business namespace")
        tables = h["sql"]("SELECT table_name FROM information_schema.tables WHERE table_schema=" + h["literal"](schema) + ";").splitlines()
        if tables:
            if set(tables) != set(TABLES):
                raise ValueError("Existing business schema differs; preserved")
            marker = h["sql"](f"SELECT source FROM `{schema}`.seed_metadata WHERE seed_id='daily-workspace-v2';").strip()
            if marker != SOURCE:
                raise ValueError("Unowned nonempty business schema; preserved")
        elif apply:
            namespace_statements.append(f"CREATE DATABASE IF NOT EXISTS `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
            for table, columns in TABLES.items():
                namespace_statements.append(f"CREATE TABLE IF NOT EXISTS `{schema}`.`{table}` ({columns}) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")
        elif not tables:
            return {"status": "BUSINESS_SCHEMAS_NOT_CREATED", "rowsReadBackExactly": 0}
        if apply:
            password = secret_values["SHOWCASE_" + key.upper() + "_RO_PASSWORD"]
            # Private generated SQL is retained under 0600; never printed. The
            # dedicated account receives SELECT on its own synthetic schema only.
            namespace_statements.extend([f"CREATE USER IF NOT EXISTS '{username}'@'%' IDENTIFIED BY '{password}';", f"GRANT SELECT ON `{schema}`.* TO '{username}'@'%';"])
        rows.extend(dataset(project, h["history_dates"]()))
    if apply and namespace_statements:
        statement = "\n".join(namespace_statements)
        path = h["PRIVATE"] / "business-schema-private.sql"
        if not path.exists(): h["private_write"](path, statement + "\n")
        h["sql"](statement)
    found, missing = history.readback(h, rows)
    statements = []
    for row in missing:
        columns = ",".join("`" + c + "`" for c in row["fields"])
        values = ",".join(h["literal"](v) for v in row["fields"].values())
        statements.append(f"INSERT INTO {history.table_sql(row['table'])} ({columns}) VALUES ({values});")
    text = "-- Synthetic business observations, never operational test receipts.\nSTART TRANSACTION;\n" + "\n".join(statements) + "\nCOMMIT;\n"
    if apply and missing:
        h["sql"](text)
        path = h["PRIVATE"] / "business-seed.sql"
        if not path.exists(): h["private_write"](path, text)
        else: h["private_write"](h["PRIVATE"] / ("business-repair-" + history.digest(text)[:12] + ".sql"), text)
    after, remaining = history.readback(h, rows) if apply else (found, missing)
    return {"status": "EXACT_SYNTHETIC_BUSINESS_READBACK" if not remaining else "MISSING_BUSINESS_ROWS", "rowsInserted": len(missing) if apply else 0, "rowsReused": len(found), "rowsReadBackExactly": len(after), "missingRows": len(remaining), "rowsIncludingSourceMarkers": len(rows), "requestsPerProject": 90 * len(h["history_dates"]()), "uniqueRequestKeysPerProject": 72 * len(h["history_dates"]()), "actualResourceMutationsClaimed": False}
