#!/usr/bin/env python3
"""Scan the selected publication history for public-release leaks."""

from __future__ import annotations

from pathlib import Path
import argparse
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
scope = parser.add_mutually_exclusive_group()
scope.add_argument("--ref", default="HEAD", help="Publication tip to audit (default: HEAD)")
scope.add_argument("--all", action="store_true", help="Include local backup and remote refs explicitly")
args = parser.parse_args()
REF_ARGS = ["--all"] if args.all else [args.ref]

FORBIDDEN_TEXT = [
    "ai-agent" + "-station",
    "Query" + "Weaver",
    "Mate" + "Claw",
    "group" + "-buy",
    "group" + "_buy",
    "s" + "-pay",
    "api." + "0-0.pro",
]

PATTERNS = [
    ("developer home path", re.compile(r"/(?:" + "Users" + r"|" + "home" + r")/[A-Za-z0-9._-]+/", re.IGNORECASE)),
    ("Windows developer home path", re.compile(r"[A-Z]:\\" + "Users" + r"\\[A-Za-z0-9._ -]+\\", re.IGNORECASE)),
    ("OpenAI-style secret", re.compile(r"(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{20,}")),
    ("GitHub token", re.compile(r"(?<![A-Za-z0-9])gh[opsu]_[A-Za-z0-9]{24,}")),
    ("bearer token", re.compile(r"\bBearer\s+[A-Za-z0-9._~+/-]{24,}={0,2}\b", re.IGNORECASE)),
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?" + "PRIVATE KEY-----")),
]


def reachable_blobs() -> list[tuple[str, str]]:
    objects = subprocess.run(
        ["git", "rev-list", "--objects", *REF_ARGS],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout
    checked = subprocess.run(
        ["git", "cat-file", "--batch-check=%(objecttype) %(objectname) %(objectsize) %(rest)"],
        cwd=ROOT,
        check=True,
        text=True,
        input=objects,
        stdout=subprocess.PIPE,
    ).stdout
    blobs: list[tuple[str, str]] = []
    for line in checked.splitlines():
        parts = line.split(" ", 3)
        if len(parts) >= 3 and parts[0] == "blob":
            path = parts[3] if len(parts) == 4 else "<unknown>"
            blobs.append((parts[1], path))
    return blobs


def scan_text(label: str, text: str, violations: list[str]) -> None:
    lower = text.lower()
    for marker in FORBIDDEN_TEXT:
        if marker.lower() in lower:
            violations.append(f"{label}: legacy/unrelated marker {marker!r}")
    for pattern_label, pattern in PATTERNS:
        if pattern.search(text):
            violations.append(f"{label}: {pattern_label}")


def scan_blobs(blobs: list[tuple[str, str]], violations: list[str]) -> int:
    process = subprocess.Popen(
        ["git", "cat-file", "--batch"],
        cwd=ROOT,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
    )
    assert process.stdin is not None
    assert process.stdout is not None
    text_blobs = 0
    try:
        for sha, path in blobs:
            process.stdin.write(f"{sha}\n".encode())
            process.stdin.flush()
            header = process.stdout.readline().decode("utf-8", errors="replace").strip()
            parts = header.split()
            if len(parts) != 3 or parts[1] != "blob":
                raise RuntimeError(f"unexpected git cat-file header: {header}")
            size = int(parts[2])
            data = process.stdout.read(size)
            process.stdout.read(1)
            if b"\0" in data:
                continue
            try:
                text = data.decode("utf-8")
            except UnicodeDecodeError:
                continue
            text_blobs += 1
            scan_text(f"blob {sha[:12]} {path}", text, violations)
    finally:
        process.stdin.close()
        process.stdout.close()
        process.wait(timeout=10)
    return text_blobs


def scan_commit_messages(violations: list[str]) -> int:
    output = subprocess.run(
        ["git", "log", *REF_ARGS, "--format=%H%x1f%B%x1e"],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout
    count = 0
    for record in output.split("\x1e"):
        record = record.strip("\n")
        if not record or "\x1f" not in record:
            continue
        sha, message = record.split("\x1f", 1)
        count += 1
        scan_text(f"commit {sha[:12]} message", message, violations)
    return count


violations: list[str] = []
blobs = reachable_blobs()
text_blob_count = scan_blobs(blobs, violations)
commit_count = scan_commit_messages(violations)

if violations:
    print("OrbisOps Git history hygiene failed:")
    for violation in violations[:100]:
        print(f"- {violation}")
    if len(violations) > 100:
        print(f"- ... {len(violations) - 100} more violation(s)")
    raise SystemExit(1)

print(
    "OrbisOps Git history hygiene passed "
    f"({commit_count} reachable commits, {text_blob_count} unique text blobs scanned)."
)
