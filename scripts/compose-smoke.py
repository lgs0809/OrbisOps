#!/usr/bin/env python3
"""Build and boot an isolated OrbisOps Compose stack with ephemeral credentials.

No generated credential is printed or written inside the repository. The script
uses a temporary env file, waits for backend/web health, verifies the web reverse
proxy reaches the protected backend, then removes containers and named volumes.
"""

from __future__ import annotations

from pathlib import Path
import json
import os
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "deploy" / "orbisops.env.example"


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def token() -> str:
    return secrets.token_urlsafe(32)


def parse_template() -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in TEMPLATE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def write_env(path: Path, values: dict[str, str]) -> None:
    path.write_text("\n".join(f"{key}={value}" for key, value in values.items()) + "\n", encoding="utf-8")
    os.chmod(path, 0o600)


def compose(project: str, env_file: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["docker", "compose", "--project-name", project, "--env-file", str(env_file), *args],
        cwd=ROOT,
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )


def get(url: str, expected: set[int]) -> int:
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            status = int(response.status)
    except urllib.error.HTTPError as error:
        status = int(error.code)
    if status not in expected:
        raise RuntimeError(f"unexpected HTTP status for {url}: {status}")
    return status


def get_json(url: str) -> dict[str, object]:
    with urllib.request.urlopen(url, timeout=5) as response:
        if int(response.status) != 200:
            raise RuntimeError(f"unexpected HTTP status for {url}: {response.status}")
        return json.loads(response.read().decode("utf-8"))


def wait_http(url: str, expected: set[int], timeout_seconds: int = 180) -> int:
    deadline = time.time() + timeout_seconds
    last_error: Exception | None = None
    while time.time() < deadline:
        try:
            return get(url, expected)
        except Exception as error:  # noqa: BLE001 - bounded smoke retry
            last_error = error
            time.sleep(2)
    raise RuntimeError(f"timed out waiting for {url}: {last_error}")


def wait_compose_health(project: str, env_file: Path, services: set[str], timeout_seconds: int = 90) -> None:
    deadline = time.time() + timeout_seconds
    last_states: dict[str, str] = {}
    while time.time() < deadline:
        status = compose(project, env_file, "ps", "--format", "json", check=False)
        containers = [json.loads(line) for line in status.stdout.splitlines() if line.strip()]
        states = {
            str(item.get("Service")): str(item.get("Health") or "")
            for item in containers
            if item.get("Service") in services
        }
        last_states = states
        if services.issubset(states.keys()) and all(states[service] == "healthy" for service in services):
            return
        time.sleep(2)
    raise RuntimeError(f"timed out waiting for compose health: {last_states}")


def main() -> int:
    values = parse_template()
    values.update(
        {
            "ORBISOPS_VERSION": "2.0.0-smoke",
            "ORBISOPS_WEB_PORT": str(free_port()),
            "ORBISOPS_SERVER_PORT": str(free_port()),
            "ORBISOPS_MYSQL_PORT": str(free_port()),
            "ORBISOPS_PGVECTOR_PORT": str(free_port()),
            "ORBISOPS_REDIS_PORT": str(free_port()),
            "ORBISOPS_MYSQL_PASSWORD": token(),
            "ORBISOPS_MYSQL_ROOT_PASSWORD": token(),
            "ORBISOPS_PGVECTOR_PASSWORD": token(),
            "ORBISOPS_REDIS_PASSWORD": token(),
            "ORBISOPS_ADMIN_JWT_SECRET": token(),
            "ORBISOPS_ADMIN_SERVICE_TOKEN": "",
            "ORBISOPS_BOOTSTRAP_ADMIN_ENABLED": "false",
            "ORBISOPS_BOOTSTRAP_ADMIN_USERNAME": "",
            "ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD": "",
            "ORBISOPS_MODEL_API_KEY": "placeholder",
            "ORBISOPS_EMBEDDING_API_KEY": "placeholder",
            "ORBISOPS_RERANK_API_KEY": "placeholder",
            "ORBISOPS_AI_MODEL_CALLS_ENABLED": "false",
            "ORBISOPS_RERANK_ENABLED": "false",
        }
    )
    project = f"orbisops-smoke-{os.getpid()}"

    with tempfile.TemporaryDirectory(prefix="orbisops-compose-smoke-") as temp_dir:
        env_file = Path(temp_dir) / "orbisops.env"
        write_env(env_file, values)
        try:
            compose(project, env_file, "config", "--quiet")
            print("compose-config=PASS")
            try:
                result = compose(project, env_file, "up", "-d", "--build")
                print(result.stdout, end="")
            except subprocess.CalledProcessError as error:
                if error.stdout:
                    print(error.stdout, end="")
                status = compose(project, env_file, "ps", check=False)
                if status.stdout:
                    print("--- compose ps ---")
                    print(status.stdout, end="")
                logs = compose(
                    project,
                    env_file,
                    "logs",
                    "--no-color",
                    "--tail",
                    "120",
                    "mysql",
                    "migrate",
                    "backend",
                    "web",
                    check=False,
                )
                if logs.stdout:
                    print("--- compose logs ---")
                    print(logs.stdout, end="")
                raise

            backend_port = values["ORBISOPS_SERVER_PORT"]
            web_port = values["ORBISOPS_WEB_PORT"]
            wait_http(f"http://127.0.0.1:{backend_port}/actuator/health", {200})
            wait_http(f"http://127.0.0.1:{web_port}/", {200})
            proxy_status = wait_http(
                f"http://127.0.0.1:{web_port}/api/v1/admin/ops-projects/snapshot",
                {401, 403},
            )

            wait_compose_health(project, env_file, {"mysql", "pgvector", "redis", "backend", "web"})

            migration = compose(
                project,
                env_file,
                "exec",
                "-T",
                "mysql",
                "sh",
                "-lc",
                'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" '
                '-e "SELECT COUNT(*) FROM orbisops_schema_history WHERE version=\'076\'"',
            ).stdout.strip().splitlines()[-1].strip()
            if migration != "1":
                raise RuntimeError(f"migration 076 missing from fresh schema history: {migration!r}")

            vector_extension = compose(
                project,
                env_file,
                "exec",
                "-T",
                "pgvector",
                "sh",
                "-lc",
                'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc '
                '"SELECT COUNT(*) FROM pg_extension WHERE extname=\'vector\'"',
            ).stdout.strip()
            if vector_extension != "1":
                raise RuntimeError(f"pgvector extension was not initialized: {vector_extension!r}")

            setup_status = get_json(f"http://127.0.0.1:{backend_port}/api/v1/setup/status")
            if setup_status.get("data") is not True:
                raise RuntimeError(f"fresh deployment did not require first-time setup: {setup_status!r}")

            print("backend-health=PASS")
            print("web-health=PASS")
            print(f"same-origin-api-proxy=PASS ({proxy_status})")
            print("migration-076=PASS")
            print("pgvector-auto-init=PASS")
            print("first-time-setup=PASS")
            return 0
        finally:
            cleanup = compose(project, env_file, "down", "--volumes", "--remove-orphans", check=False)
            if cleanup.stdout:
                print(cleanup.stdout, end="")


if __name__ == "__main__":
    raise SystemExit(main())
