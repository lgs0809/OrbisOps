#!/usr/bin/env python3
"""Validate the public deployment environment before Docker Compose starts."""

from __future__ import annotations

from pathlib import Path
import sys

PLACEHOLDERS = {"", "[REDACTED_SECRET]", "changeme", "change-me", "placeholder"}
REQUIRED = (
    "ORBISOPS_MYSQL_PASSWORD",
    "ORBISOPS_MYSQL_ROOT_PASSWORD",
    "ORBISOPS_PGVECTOR_PASSWORD",
    "ORBISOPS_REDIS_PASSWORD",
    "ORBISOPS_ADMIN_JWT_SECRET",
)


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def invalid(value: str | None) -> bool:
    return value is None or value.strip().lower() in {item.lower() for item in PLACEHOLDERS}


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "deploy/orbisops.env")
    if not path.is_file():
        print(f"deployment env file not found: {path}", file=sys.stderr)
        print("copy deploy/orbisops.env.example to deploy/orbisops.env and configure it first", file=sys.stderr)
        return 2

    values = parse_env(path)
    errors = [key for key in REQUIRED if invalid(values.get(key))]

    if values.get("ORBISOPS_BOOTSTRAP_ADMIN_ENABLED", "false").lower() == "true":
        for key in ("ORBISOPS_BOOTSTRAP_ADMIN_USERNAME", "ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD"):
            if invalid(values.get(key)):
                errors.append(key)

    if errors:
        print("OrbisOps deployment preflight failed; configure:", file=sys.stderr)
        for key in sorted(set(errors)):
            print(f"  - {key}", file=sys.stderr)
        return 2

    print("OrbisOps deployment preflight passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
