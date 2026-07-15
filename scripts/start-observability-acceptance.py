#!/usr/bin/env python3
"""Start the optional business evidence profile after local-acceptance + seed-local-acceptance.

Retains all volumes and existing credentials. Only the acceptance backend's two MCP sidecars
are stopped for a network-namespace refresh; the target's traffic/data remain independent.
"""
from pathlib import Path
import runpy
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
env_path = ROOT / "deploy/.env.acceptance"
values = dict(line.split("=", 1) for line in env_path.read_text().splitlines() if line and not line.startswith("#"))
if not values.get("OPS_ACCEPTANCE_OBSERVABILITY_TOKEN"):
    with env_path.open("a") as stream:
        stream.write("\nOPS_ACCEPTANCE_OBSERVABILITY_TOKEN=" + secrets.token_urlsafe(36) + "\n")
env_path.chmod(0o600)
compose = runpy.run_path(str(ROOT / "scripts/local-acceptance.py"))["compose"]
compose("--profile", "business", "up", "-d", "prometheus-acceptance", "elasticsearch-acceptance")
subprocess.run(["python3", str(ROOT / "scripts/seed-workflow-target.py")], cwd=ROOT, check=True)
compose("--profile", "business", "build", "observability-mcp")
compose("--profile", "business", "stop", "mcp-acceptance", "observability-mcp")
compose("--profile", "business", "up", "-d", "--no-build", "backend", "mcp-acceptance", "observability-mcp")
print("Observability profile started. Review tools using seed-observability-mcp.py; no workflow verdict has been produced.")
