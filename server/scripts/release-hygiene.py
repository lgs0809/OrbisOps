#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__).resolve()
SKIP_DIRS = {"target", ".git", ".idea", ".vscode", "node_modules", "dist", "build", "__pycache__"}
TEXT_SUFFIXES = {".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".json", ".jsonl", ".sh", ".mjs", ".js", ".ts", ".tsx", ".html", ".toml", ".txt", ".sql", ".py", ".conf"}
TEXT_NAMES = {"pom.xml", "Dockerfile", ".gitignore"}

FORBIDDEN = {
    "legacy package": "cn." + "bugstack",
    "legacy author domain": "bug" + "stack.cn",
    "legacy author": "xiao" + "fuge",
    "legacy author handle": "fuzheng" + "wei",
    "unrelated business fixture": "group" + "-buy",
    "unrelated business fixture id": "group" + "_buy",
    "unrelated business fixture zh": "拼" + "团",
    "unrelated payment fixture": "s" + "-pay",
    "legacy product name": "AI Agent " + "Station",
    "legacy artifact name": "orbisops-" + "study",
    "legacy database prefix": "agent" + "_station",
    "legacy config prefix": "xfg." + "wrench",
    "private local home": "/workspace/" + "developer",
    "private model endpoint": "api." + "0-0.pro",
}

SECRET_PATTERNS = [
    re.compile(r"(?i)api[-_]?key\s*[:=]\s*sk-[A-Za-z0-9_-]{12,}"),
    re.compile(r"(?i)(password|secret|token)\s*[:=]\s*(123456|admin|root|postgres)\b"),
    re.compile(r"registry\.cn-[^\s/]+\.aliyuncs\.com/xfg"),
]


def text_file(path: Path) -> bool:
    return path.name in TEXT_NAMES or path.suffix.lower() in TEXT_SUFFIXES


violations: list[str] = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.resolve() == SELF:
        continue
    if any(part in SKIP_DIRS for part in path.parts) or not text_file(path):
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    rel = path.relative_to(ROOT)
    for label, marker in FORBIDDEN.items():
        if marker in text:
            violations.append(f"{rel}: {label}")
    if "src/test/" not in rel.as_posix():
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                violations.append(f"{rel}: secret-like literal")

if violations:
    print("OrbisOps release hygiene failed:", file=sys.stderr)
    for violation in sorted(set(violations)):
        print(f"  - {violation}", file=sys.stderr)
    raise SystemExit(1)

print("OrbisOps release hygiene passed.")
