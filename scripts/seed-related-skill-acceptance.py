#!/usr/bin/env python3
"""Replay the manual browser fixture without overwriting existing Skill data.

Creates only missing named fixtures through normal APIs. Existing entries are
preserved, including subsequent user edits. This does not seed successful tasks,
model proposals, approvals, or evaluation results.
"""
import json
import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    fixture = json.loads((ROOT / "scripts/fixtures/ops06-related-manual-package.json").read_text())
    runtime = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = runtime["api"], runtime["token"]
    base = "/api/v1/admin/ops/skills"
    command = fixture["globalV1"]
    existing = [s for s in api(base + "/global", token=token) if s["skillId"] == command["skillId"]]
    actions = []
    if existing:
        actions.append("PRESERVED_EXISTING_GLOBAL")
    else:
        created = api(base + "/global", "POST", command, token)
        # The CAS guards protect a user edit between creation and this V2 save.
        update = dict(fixture["globalV2"], baseVersion=created["currentVersion"],
                      baseSkillHash=created["currentSkillHash"])
        api(base + "/global/" + command["skillId"], "PUT", update, token)
        actions.append("CREATED_GLOBAL_V1_AND_V2")
    project = base + "/projects/" + fixture["projectId"]
    target = fixture["projectCopy"]
    existing = [s for s in api(project, token=token) if s["skillId"] == target["skillId"]]
    if existing:
        actions.append("PRESERVED_EXISTING_PROJECT")
    else:
        current = api(base + "/global/" + command["skillId"], token=token)
        if current["content"] != fixture["globalV2"]["content"] or current["origin"] != "MANUAL":
            raise RuntimeError("Existing global fixture differs; retained without copying unknown content")
        api(project + "/copy-from-global", "POST", target, token)
        actions.append("COPIED_GLOBAL_TO_PROJECT")
    return {"fixtureOrigin": fixture["fixtureOrigin"], "actions": actions,
            "projectId": fixture["projectId"], "skillId": target["skillId"]}


if __name__ == "__main__":
    print(json.dumps(prepare(), ensure_ascii=False, indent=2))
