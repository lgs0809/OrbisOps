#!/usr/bin/env python3
"""Create/reuse an explicitly incomplete candidate from a real completed investigation session.

Normal PREPARE compiles the authoritative runtime context. No approval, Landing,
resource version or success state is inserted. Repeated imports preserve the candidate.
"""
from pathlib import Path
import argparse
import json
import runpy

ROOT = Path(__file__).resolve().parents[1]
OBJECTIVE = "OPS-04 C 验收：依据实际慢请求调查保存待完善候选方案；尚未执行资源变更，不能宣称已落地或验收成功。"


def prepare(session):
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = support["api"], support["token"]
    project = "ops-acceptance-a"
    candidates = api("/api/v1/user/ops/change-packages?projectId=" + project, token=token)
    matches = [item for item in candidates if item.get("sessionId") == session and item.get("objective") == OBJECTIVE]
    if len(matches) > 1:
        raise RuntimeError("Ambiguous candidate identity; existing packages preserved")
    if matches:
        return api("/api/v1/user/ops/change-packages/" + matches[0]["packageId"], token=token)
    return api("/api/v1/user/chat/sessions/" + session + "/change-packages", "POST", {
        "projectId": project, "serviceId": "ops-acc-a-service-4", "targetEnvironment": "acceptance",
        "objective": OBJECTIVE, "question": "保留 A/B 真实慢请求证据，记录实际 Landing 与批准验收标准尚缺失。", "mcpSteps": []}, token)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-session", required=True, help="A real completed A/B session owned by the acceptance admin")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = prepare(args.source_session)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: result.get(key) for key in ("packageId", "status", "version", "packageHash", "packageType", "contextBundleId")}, ensure_ascii=False))
