#!/usr/bin/env python3
"""Idempotently publish the read-only OPS-05 fixture and prepare a reusable manual session.

Uses existing isolated business dependencies. Does not create model decisions,
episode assignments, approvals or successful business outcomes in SQL.
"""
from pathlib import Path
import json
import os
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"


def matches_source(expected, actual):
    if isinstance(expected, dict):
        return isinstance(actual, dict) and all(k in actual and matches_source(v, actual[k]) for k, v in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(expected) == len(actual) and all(matches_source(a, b) for a, b in zip(expected, actual))
    return expected == actual


def prepare():
    m = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = m["api"], m["token"]
    providers = api(f"/api/v1/admin/ops/projects/{PROJECT}/tools", token=token)
    providers = [p for p in providers if p.get("status") == "ENABLED" and "target_version" in p.get("allowedActions", [])]
    if len(providers) != 1:
        raise RuntimeError("Exactly one reviewed local observability MCP must expose target_version; run the OPS-04 seed first")
    source = json.loads((ROOT / "scripts/fixtures/workflow-episode-read.json").read_text()
                        .replace("$observabilityMcpId", providers[0]["mcpId"]))
    path = "/api/v1/admin/ops-agents/" + source["agentId"]
    versions = api(path + "/versions", token=token)
    matches = [v for v in versions if matches_source(source, v)]
    if matches:
        saved = matches[0]
        if saved.get("lifecycle") not in ("PUBLISHED", "DRAFT", "VALIDATED"):
            raise RuntimeError("Existing fixture is not published; retained without silent reactivation")
    elif versions:
        raise RuntimeError("Existing workflow ID belongs to different content; retained")
    else:
        saved = api("/api/v1/admin/ops-agents/drafts", "POST", source, token)
    if saved.get("lifecycle") != "PUBLISHED":
        for action in ("validate", "publish"):
            saved = api(path + "/versions/" + str(saved["version"]) + "/" + action, "POST", {}, token)
    fixture = ROOT / "deploy/.acceptance-private/task-episodes.json"
    if fixture.exists():
        info = json.loads(fixture.read_text())
        if info.get("agentHash") != saved["definitionHash"]:
            raise RuntimeError("Saved manual fixture references a different workflow version")
    else:
        session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "agentId": saved["agentId"],
            "agentVersion": saved["version"], "title": "OPS-05 手动验收 · 跨轮任务归属"}, token)
        info = {"projectId": PROJECT, "sessionId": session, "agentId": saved["agentId"],
                "agentVersion": saved["version"], "agentHash": saved["definitionHash"]}
        with os.fdopen(os.open(fixture, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as stream:
            json.dump(info, stream, ensure_ascii=False, indent=2)
    return info


if __name__ == "__main__":
    print(json.dumps(prepare(), ensure_ascii=False, indent=2))
