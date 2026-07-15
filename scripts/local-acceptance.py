#!/usr/bin/env python3
"""Persistent isolated acceptance environment; never touches the default stack.

init creates credentials once; up builds and migrates; stop retains all data.
Model calls are disabled initially. enable-acceptance-models.py can connect an
existing authorized provider; deterministic fixtures do not need model calls.
"""
from pathlib import Path
import argparse
import os
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ENV = ROOT / "deploy" / ".env.acceptance"
PROJECT = "orbisops-acceptance"


def initialize():
    if ENV.exists():
        existing = ENV.read_text()
        if not any(line.startswith("OPS_ACCEPTANCE_MCP_TOKEN=") for line in existing.splitlines()):
            with ENV.open("a") as stream:
                stream.write("\nOPS_ACCEPTANCE_MCP_TOKEN=" + secrets.token_urlsafe(36) + "\n")
        print("Using existing acceptance credentials (unchanged).")
        return
    values = {}
    for line in (ROOT / "deploy" / "orbisops.env.example").read_text().splitlines():
        if line.strip() and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    values.update({
        "ORBISOPS_COMPOSE_PROJECT_NAME": PROJECT,
        "ORBISOPS_VERSION": "2.0.0-acceptance",
        "ORBISOPS_WEB_PORT": "3302", "ORBISOPS_SERVER_PORT": "18089",
        "ORBISOPS_MYSQL_PORT": "13362", "ORBISOPS_PGVECTOR_PORT": "15462",
        "ORBISOPS_REDIS_PORT": "16362",
        "ORBISOPS_MYSQL_DATABASE": "orbisops_acceptance",
        "ORBISOPS_PGVECTOR_DATABASE": "orbisops_acceptance",
        "ORBISOPS_AI_MODEL_CALLS_ENABLED": "false",
        "ORBISOPS_MODEL_BASE_URL": "https://model-provider.invalid",
        "ORBISOPS_MODEL_API_KEY": "placeholder", "ORBISOPS_CHAT_MODEL": "unconfigured",
        "ORBISOPS_EMBEDDING_BASE_URL": "https://embedding-provider.invalid",
        "ORBISOPS_EMBEDDING_API_KEY": "placeholder", "ORBISOPS_EMBEDDING_MODEL": "unconfigured",
        "ORBISOPS_RERANK_ENABLED": "false", "ORBISOPS_RERANK_API_KEY": "",
        "ORBISOPS_ADMIN_SERVICE_TOKEN": "",
        "ORBISOPS_BOOTSTRAP_ADMIN_ENABLED": "false",
        "ORBISOPS_BOOTSTRAP_ADMIN_USERNAME": "", "ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD": "",
    })
    for key in ("ORBISOPS_MYSQL_PASSWORD", "ORBISOPS_MYSQL_ROOT_PASSWORD",
                "ORBISOPS_PGVECTOR_PASSWORD", "ORBISOPS_REDIS_PASSWORD", "ORBISOPS_ADMIN_JWT_SECRET", "OPS_ACCEPTANCE_MCP_TOKEN"):
        values[key] = secrets.token_urlsafe(36)
    with os.fdopen(os.open(ENV, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as stream:
        stream.write("\n".join(f"{key}={value}" for key, value in values.items()) + "\n")
    print("Created private deploy/.env.acceptance; first admin uses the normal setup page.")


def compose(*args):
    return subprocess.run(["docker", "compose", "--project-name", PROJECT, "--env-file", str(ENV),
                           "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml", *args],
                          cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["init", "up", "start", "stop", "status", "logs", "config"])
    args = parser.parse_args()
    initialize()
    if args.action == "up":
        compose("up", "-d", "--build")
    elif args.action == "start":
        compose("up", "-d", "--no-build")
    elif args.action == "stop":
        compose("stop")
    elif args.action == "status":
        compose("ps", "-a")
    elif args.action == "logs":
        compose("logs", "--no-color", "--tail", "100")
    elif args.action == "config":
        compose("config", "--quiet")
    print("Acceptance UI: http://127.0.0.1:3302 | API: http://127.0.0.1:18089")


if __name__ == "__main__":
    main()
