#!/usr/bin/env python3
"""Repository-wide public release hygiene gate.

Only Git-tracked text files are scanned. Generated logs, build outputs and local
caches therefore cannot hide a release issue or create machine-specific false
positives.
"""

from __future__ import annotations

from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__).resolve()

TEXT_SUFFIXES = {
    ".conf", ".css", ".env", ".html", ".java", ".js", ".json", ".jsonl",
    ".md", ".mjs", ".properties", ".py", ".sh", ".sql", ".toml", ".ts",
    ".tsx", ".txt", ".xml", ".yaml", ".yml",
}
TEXT_NAMES = {"Dockerfile", "Makefile", ".gitignore", ".dockerignore"}

FORBIDDEN_MARKERS = {
    "legacy package": "cn." + "bugstack",
    "legacy author domain": "bug" + "stack.cn",
    "legacy author": "xiao" + "fuge",
    "legacy author handle": "fuzheng" + "wei",
    "legacy product name": "AI Agent " + "Station",
    "legacy artifact name": "ai-agent" + "-station-study",
    "legacy database prefix": "agent" + "_station",
    "legacy config prefix": "xfg." + "wrench",
    "unrelated business fixture": "group" + "-buy",
    "unrelated business fixture id": "group" + "_buy",
    "unrelated business fixture zh": "拼" + "团",
    "unrelated payment fixture": "s" + "-pay",
    "private model endpoint": "api." + "0-0.pro",
    "unrelated query product": "Query" + "Weaver",
    "unrelated knowledge-base project": "enterprise-knowledge" + "-base",
}

RUNTIME_FORBIDDEN_MARKERS = {
    "retired repair workspace marker": "ai-agent" + "-repair",
    "placeholder absolute runtime path": "/absolute/" + "path",
    "fixed bootstrap administrator id": "bootstrap" + "-admin",
}

PATH_PATTERNS = [
    re.compile(r"/" + r"Users/[A-Za-z0-9._-]+/"),
    re.compile(r"/" + r"home/[A-Za-z0-9._-]+/"),
    re.compile(r"(?i)[A-Z]:\\" + r"Users\\[A-Za-z0-9._ -]+\\"),
]

SECRET_PATTERNS = [
    ("OpenAI-style secret", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")),
    ("bearer token", re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/-]{24,}={0,2}\b")),
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("GitHub token", re.compile(r"\bgh[opsu]_[A-Za-z0-9]{24,}\b")),
]


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item]


def is_text_file(path: Path) -> bool:
    return path.name in TEXT_NAMES or path.suffix.lower() in TEXT_SUFFIXES


def is_runtime_source(rel: str) -> bool:
    if not (rel.startswith("server/") or rel.startswith("web/src/")):
        return False
    if "/src/test/" in rel or "/e2e/" in rel or ".test." in rel or ".spec." in rel:
        return False
    if rel.endswith("application-test.yml") or rel.endswith("application-test.yaml"):
        return False
    return "/src/main/" in rel or rel.startswith("web/src/")


violations: list[str] = []
for path in tracked_files():
    if path.resolve() == SELF or not path.is_file() or not is_text_file(path):
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    rel = path.relative_to(ROOT).as_posix()

    for label, marker in FORBIDDEN_MARKERS.items():
        if marker in text:
            violations.append(f"{rel}: {label}")

    if is_runtime_source(rel):
        for label, marker in RUNTIME_FORBIDDEN_MARKERS.items():
            if marker in text:
                violations.append(f"{rel}: {label}")

    for pattern in PATH_PATTERNS:
        if pattern.search(text):
            violations.append(f"{rel}: developer absolute path")

    for label, pattern in SECRET_PATTERNS:
        if pattern.search(text):
            violations.append(f"{rel}: {label}")

    for line_number, line in enumerate(text.splitlines(), start=1):
        if line.endswith((' ', '\t')):
            violations.append(f"{rel}:{line_number}: trailing whitespace")

if violations:
    print("OrbisOps repository release hygiene failed:")
    for violation in sorted(set(violations)):
        print(f"  - {violation}")
    raise SystemExit(1)

print("OrbisOps repository release hygiene passed.")
