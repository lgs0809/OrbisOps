#!/usr/bin/env python3
"""Synthetic relational history for only orbisops_showcase.

Called by showcase-workspace.py. Deterministic IDs and row comparisons reject
collisions or edited history. This is presentation content, not a regression
test, model transcript or actual operational approval/landing receipt.
"""
from collections import Counter
import datetime as dt
import hashlib
import json

SOURCE = "synthetic-demonstration:orbisops-daily-workspace-v2"


def digest(value):
    raw = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode()).hexdigest()


def workflows(project):
    """Reviewed definitions; normal API compilation owns hashes/publication."""
    common = {"schemaVersion": 1, "projectId": project["projectId"], "engine": "HYBRID",
              "phase": "INVESTIGATE", "definitionKind": "SPECIALIZED_WORKFLOW",
              "workflowInvocationMode": "MANUAL_ONLY", "workflowAutoSelectEnabled": False,
              "queryRewriteEnabled": False, "ragEnabled": False, "changePackageEnabled": False,
              "startNodeId": "start", "defaultMaxMainRounds": 5, "defaultSubAgentMaxIterations": 2}
    definitions = []
    for kind, name, instruction in (
        ("inspection", "日常巡检", project["inspectionQuestion"]),
        ("review", "处理建议复核", "复核用户给出的调查事实和处理建议，分开事实、推测、风险与待确认事项。只读调查，不执行生产或测试变更。批准本工作流只表示建议可继续讨论，不授予资源写权限。")):
        aid = "showcase-" + project["key"] + "-" + kind
        nodes = [
            {"nodeId": "start", "type": "START", "config": {"inputKeys": ["query"], "position": {"x": 100, "y": 60}}},
            {"nodeId": "investigate", "type": "AGENT", "mode": "REACT", "agent": "daily-evidence-reader",
             "modelId": "showcase-luna", "outputKey": "findings", "instruction": instruction,
             "description": "整理授权范围内的证据与缺口", "config": {"inheritProjectCapabilities": True,
             "position": {"x": 100, "y": 250}}}]
        if kind == "review":
            nodes.append({"nodeId": "human-review", "type": "HUMAN_APPROVAL", "outputKey": "review",
                          "description": "确认处理建议范围；不授予写权限", "config": {"timeoutSeconds": 3600, "position": {"x": 100, "y": 420}}})
        nodes.extend([
            {"nodeId": "report", "type": "AGENT", "mode": "LLM", "agent": "daily-report-writer",
             "modelId": "showcase-terra", "outputKey": "report", "instruction": "使用已有证据编写清晰的交接摘要：现象、证据来源、已确认事实、未确认事项、下一步。不猜测缺失数据，不宣称执行过未执行的操作。",
             "description": "整理可读交接摘要", "config": {"inheritProjectCapabilities": False, "position": {"x": 100, "y": 600}}},
            {"nodeId": "end", "type": "END", "config": {"outputKeys": ["report"], "position": {"x": 100, "y": 800}}}])
        definitions.append({**common, "agentId": aid, "name": project["name"] + " · " + name,
                            "description": "日常值班方法。展示库默认关闭模型调用；发布只证明定义结构，不代表已执行调查或生产变更。",
                            "instruction": instruction, "nodes": nodes,
                            "edges": [{"from": a["nodeId"], "to": b["nodeId"], "conditionType": "always", "condition": "always"}
                                      for a, b in zip(nodes, nodes[1:])]})
    return definitions


def configure(h):
    api, token = h["api"], h["login"]()
    spec = json.loads(h["CATALOG"].read_text())
    # This is an unusable synthetic provider with no real credential. The
    # global model gate remains off; enabled model metadata permits graph
    # compilation but never proves provider connectivity.
    apis = api("/api/v1/admin/ai-client-api/query-all", token=token)
    if not any(item["apiId"] == "showcase-unconfigured-provider" for item in apis):
        api("/api/v1/admin/ai-client-api/create", "POST", {
            "apiId": "showcase-unconfigured-provider", "providerName": "本地展示 · 模型未连接",
            "providerType": "OPENAI", "baseUrl": "https://model-provider.invalid", "apiKey": "placeholder",
            "completionsPath": "/v1/chat/completions", "embeddingsPath": "/v1/embeddings", "status": 0}, token)
    models = api("/api/v1/admin/ai-client-model/query-all", token=token)
    for role in ("luna", "terra"):
        mid = "showcase-" + role
        if not any(item["modelId"] == mid for item in models):
            api("/api/v1/admin/ai-client-model/create", "POST", {
                "modelId": mid, "apiId": "showcase-unconfigured-provider", "modelName": {"luna": "gpt-6-luna", "terra": "gpt-6.1-terra"}[role],
                "modelType": "openai", "modelUsage": "CHAT", "description": "展示定义引用；未连接真实 provider，模型总闸关闭。", "status": 1}, token)
    result = {"workflows": [], "skills": [], "schedules": [], "resources": [], "mcps": [], "targets": [], "alertRules": []}
    values = h["accounts"]()
    for project in spec["projects"]:
        pid, key = project["projectId"], project["key"]
        snapshot = api("/api/v1/admin/ops-projects/snapshot", token=token)
        existing_project = next(entry for entry in snapshot["projects"] if entry["projectId"] == pid)
        resource_id = "showcase-" + key + "-observations"
        resource_body = {"projectId": pid, "resourceId": resource_id, "name": project["name"] + " · 只读业务观察",
            "type": "mysql", "environment": "test", "endpoint": "jdbc:mysql://mysql:3306/showcase_" + key + "_business?allowPublicKeyRetrieval=true&useSSL=false",
            "username": "showcase_" + key + "_ro", "passwordRef": "${env:SHOWCASE_" + key.upper() + "_RO_PASSWORD}"}
        resource = next((item for item in existing_project.get("resources", []) if item["resourceId"] == resource_id), None)
        if resource is None:
            created_project = api("/api/v1/admin/ops-projects/resources", "POST", resource_body, token)
            resource = next(item for item in created_project["resources"] if item["resourceId"] == resource_id)
        elif any(resource.get(field) != resource_body[field] for field in ("name", "type", "environment", "endpoint")):
            raise ValueError("Existing resource differs; retained")
        if resource.get("status") != "SCANNED":
            raise ValueError("Showcase readonly resource has not actually scanned its local schema")
        result["resources"].append({"projectId": pid, "resourceId": resource_id, "status": resource["status"]})
        mcp_id = pid + "-" + resource_id + "-readonly-mcp"
        mcp = next((item for item in existing_project.get("generatedMcps", []) if item["mcpId"] == mcp_id), None)
        if mcp is None:
            created_project = api("/api/v1/admin/ops-projects/mcps/generate", "POST", {"projectId": pid, "resourceId": resource_id, "profile": "readonly"}, token)
            mcp = next(item for item in created_project["generatedMcps"] if item["mcpId"] == mcp_id)
        result["mcps"].append({"projectId": pid, "mcpId": mcp.get("mcpId"), "resourceId": resource_id})
        target_path = "/api/v1/admin/ops/projects/" + pid + "/execution-targets"
        existing_targets = api(target_path, token=token)
        for purpose, target_name in (("release", "测试发布目标"), ("config", "配置核对目标")):
            target_id = "showcase-" + key + "-" + purpose
            desired = {"resourceId": target_id, "projectId": pid, "name": project["name"] + " · " + target_name,
                "workerId": "showcase-unconnected-worker", "adapter": "deployment-http", "environments": ["test"],
                "configuration": {"baseUrl": "http://127.0.0.1:1/showcase/" + key + "/" + purpose,
                    "syntheticDemonstration": True, "source": SOURCE, "serviceIds": [v["serviceId"] for v in project["services"]]},
                "status": "DISABLED"}
            target = next((item for item in existing_targets if item["resourceId"] == target_id), None)
            if target is None:
                target = api(target_path + "/" + target_id, "PUT", desired, token)
            elif any(target.get(field) != desired[field] for field in ("name", "workerId", "adapter", "status", "configuration")):
                raise ValueError("Existing target differs; retained")
            result["targets"].append({"projectId": pid, "resourceId": target_id, "status": target["status"]})
        skills_path = "/api/v1/admin/ops/skills/projects/" + pid
        catalog = api(skills_path, token=token)
        for i, skill in enumerate(project["skills"]):
            sid = "showcase-" + key + "-" + skill
            document = h["ROOT"] / "scripts/fixtures/daily-workspace/knowledge" / project["documents"][i]
            content = document.read_text()
            current = next((entry for entry in catalog if entry["skillId"] == sid), None)
            if current is None:
                current = api(skills_path, "POST", {"skillId": sid,
                    "name": project["name"] + (" · 证据核对" if i == 0 else " · 值班排障"),
                    "description": "人工编写的方法基线，保留证据来源、幂等与审批边界。非自动进化成果。",
                    "content": content, "status": "ENABLED", "origin": "MANUAL", "updateMode": "MANUAL_ONLY",
                    "autoUpdateEnabled": False, "autoMergeEnabled": False, "category": "GENERAL",
                    "subcategory": skill, "keywords": [project["name"], skill],
                    "whenToUse": ["当前项目的交接与只读调查"], "whenNotToUse": ["授予生产操作权限", "确认没有证据的根因"]}, token)
            else:
                versions = api(skills_path + "/" + sid + "/versions", token=token)
                initial = next((entry for entry in versions if entry.get("version") == 1), None)
                if initial is None or initial.get("content") != content:
                    raise ValueError("Existing hand-authored Skill differs; retained")
            result["skills"].append({"projectId": pid, "skillId": sid, "currentVersion": current.get("currentVersion"),
                                      "skillHash": current.get("currentSkillHash"), "source": "MANUAL"})
        for definition in workflows(project):
            aid = definition["agentId"]
            versions = api("/api/v1/admin/ops-agents/" + aid + "/versions", token=token)
            if not versions:
                current = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
            else:
                current = versions[0]
                # Explicit nodes/instructions protect an existing user-edited graph.
                if current.get("projectId") != pid or current.get("instruction") != definition["instruction"] \
                        or [n["nodeId"] for n in current["nodes"]] != [n["nodeId"] for n in definition["nodes"]]:
                    raise ValueError("Existing Workflow differs; retained")
            if current["lifecycle"] in ("DRAFT", "VALIDATED"):
                for action in ("validate", "publish"):
                    current = api("/api/v1/admin/ops-agents/" + aid + "/versions/" + str(current["version"]) + "/" + action, "POST", {}, token)
            elif current["lifecycle"] != "PUBLISHED":
                raise ValueError("Existing Workflow intentionally disabled; retained")
            result["workflows"].append({field: current.get(field) for field in ("projectId", "agentId", "version", "definitionHash", "lifecycle")})
        published = next(item for item in result["workflows"] if item["projectId"] == pid and item["agentId"].endswith("-inspection"))
        rule_path = "/api/v1/admin/ops/alert-triggers/rules"
        rule_name = project["name"] + " · 值班告警"
        desired_rule = {"ruleName": rule_name, "status": 0, "sourceType": "ALERTMANAGER", "projectId": pid,
            "agentDefinitionId": published["agentId"], "agentBindingMode": "PINNED_VERSION", "agentVersion": published["version"],
            "agentDefinitionHash": published["definitionHash"], "alertNameRegex": "^Showcase.*$", "severityRegex": "^(warning|critical)$",
            "serviceRegex": "^(" + "|".join(v["serviceId"] for v in project["services"]) + ")$",
            "matchLabelsJson": json.dumps({"projectId": pid, "environment": "test"}),
            "webhookSecret": dict(line.split("=",1) for line in h["ENV"].read_text().splitlines() if "=" in line)["SHOWCASE_WEBHOOK_SECRET"],
            "notifyChannel": False, "includeRecentLogs": True, "rangeMinutes": 30, "promWindow": "5m", "nodeTimeoutSeconds": 120,
            "maxEvidenceItems": 20, "subAgentMaxIterations": 2, "dedupWindowSeconds": 300,
            "questionTemplate": "请调查${service}的${summary}，保留同时间窗与重复接收证据，缺少资料时明确说明，不执行资源变更。"}
        rule = next((item for item in api(rule_path, token=token) if item["projectId"] == pid and item["ruleName"] == rule_name), None)
        if rule is None: rule = api(rule_path, "POST", desired_rule, token)
        elif any(rule.get(field) != desired_rule[field] for field in ("status", "agentDefinitionId", "agentVersion", "agentDefinitionHash", "questionTemplate")):
            raise ValueError("Existing alert rule differs; retained")
        result["alertRules"].append({"projectId": pid, "id": rule["id"], "ruleName": rule_name, "status": rule["status"]})
        schedules_path = "/api/v1/admin/task-schedule"
        for hour, name in ((9, "早班巡检"), (17, "交接巡检")):
            task_name = project["name"] + " · " + name
            old = next((t for t in api(schedules_path + "/list?projectId=" + pid, token=token) if t["taskName"] == task_name), None)
            desired = {"projectId": pid, "executionType": "WORKFLOW", "agentId": "showcase-" + key + "-inspection",
                "agentBindingMode": "PINNED_VERSION", "agentVersion": 1, "taskName": task_name,
                "description": "每日值班配置；默认暂停，手动执行前须配置真实模型与资源。",
                "cronExpression": f"0 0 {hour} * * *", "status": 0, "taskParam": project["inspectionQuestion"],
                "notifyChannel": False, "lightweightScreeningEnabled": False, "rangeMinutes": 30,
                "promWindow": "5m", "nodeTimeoutSeconds": 120, "maxEvidenceItems": 20, "subAgentMaxIterations": 2}
            if old is None:
                api(schedules_path + "/create", "POST", desired, token)
                old = next(t for t in api(schedules_path + "/list?projectId=" + pid, token=token) if t["taskName"] == task_name)
            elif any(old.get(field) != desired[field] for field in ("agentId", "taskParam", "status", "executionType")):
                raise ValueError("Existing schedule differs; retained")
            result["schedules"].append({"id": old["id"], "projectId": pid, "taskName": task_name})
    h["private_write"](h["PRIVATE"] / "catalog-receipt.json", h["canonical"](result) + "\n", replace=True)
    return result


def histories(h, configured):
    rows = []
    spec = json.loads(h["CATALOG"].read_text())
    users = h["accounts"]()
    owner, reviewer, viewer = users["operator"]["userId"], users["reviewer"]["userId"], users["lead"]["userId"]
    dates = h["history_dates"]()
    counter = 850000
    def add(table, key, fields):
        nonlocal counter
        counter += 1
        # Surrogate keys are stable for keyless historical timelines/messages.
        rows.append({"table": table, "key": key, "fields": fields})
    def event_row(table, fields):
        nonlocal counter
        counter += 1
        identity = counter
        add(table, {"id": counter}, {"id": counter, **fields})
        return identity
    for pi, project in enumerate(spec["projects"]):
        pid, key = project["projectId"], project["key"]
        agent = next(item for item in configured["workflows"] if item["agentId"] == "showcase-" + key + "-inspection")
        repository = "showcase-repository-" + key
        add("ai_ops_source_repository", {"repository_id": repository}, {"repository_id": repository, "project_id": pid,
            "name": project["name"] + " · 示例服务目录", "local_path": "/nonexistent/showcase/" + key,
            "default_revision": "showcase-history", "default_commit_sha": "0" * 40, "status": "DISABLED", "created_by": owner})
        for service in project["services"]:
            add("ai_ops_project_service", {"project_id": pid, "service_id": service["serviceId"]}, {
                "project_id": pid, "service_id": service["serviceId"], "name": service["name"], "repository_id": repository,
                "module_path": service["serviceId"], "build_profile": "SHOWCASE_UNCONNECTED", "status": "DISABLED",
                "deployment_resource_id": "showcase-" + key + "-release", "health_url": "", "smoke_urls_json": []})
        run_ids = []
        for n, day in enumerate(dates, 1):
            scenario = (n - 1) % 8
            selected = next(item for item in configured["workflows"] if item["agentId"] == "showcase-" + key + ("-review" if scenario == 7 else "-inspection"))
            sid, rid = f"showcase-session-{key}-{n:02}", f"showcase-run-{key}-{n:02}"
            run_ids.append(rid)
            stamp = day.isoformat() + f" {pi:02}:20:00"
            iso = stamp.replace(" ", "T") + "Z"
            state = ("SUCCEEDED", "SUCCEEDED", "FAILED", "SUCCEEDED", "CANCELED", "SUCCEEDED", "FAILED", "SUCCEEDED")[scenario]
            titles = ("早班交接与服务概况", "重复请求核对", "服务超时调查", "共享依赖与告警关联", "取消扩大调查", "晚班巡检交接", "证据不足与补采建议", "处理方案复核")
            subjects = [service["name"] for service in project["services"]]
            scenarios = (
                (f"我来接早班，先整理{project['name']}有哪些服务、共用依赖和需要留意的观察记录。", f"交接范围为{subjects[0]}、{subjects[1]}和{subjects[2]}，共同依赖为{project['services'][0]['dependency']}。当日台账有90条接收记录、72个请求标识；18次重复接收不能算新业务。当前窗口可用性仍需实时资料确认。"),
                (f"{subjects[1]}的重复请求看起来增多了，帮我分开接收次数与实际业务数量。", "固定台账中90次接收对应72个请求标识，重复量为18。建议按服务、请求标识和时间窗口检查，再核对业务状态；只看日志条数会夸大业务量。尚未证明重复请求造成重复处理。"),
                (f"{subjects[0]}超时了，能不能先核对同时间窗里的相关服务？", f"{subjects[0]}与{subjects[1]}有不同症状，但当前窗口日志不完整，本轮未完成证据收集。不能仅凭共享依赖确认根因。下一步补采相同窗口的延迟、失败率与依赖连接状态。"),
                (f"{subjects[0]}响应变慢，{subjects[1]}同时访问依赖超时，请把相关警告归到一起看。", f"两条不同症状具备相近时间窗及共同依赖{project['services'][0]['dependency']}，展示记录将其归入同一调查组，原始警告仍保留。归组便于调查，不等于已经确认根因；还需对照请求链路。"),
                ("先取消扩大范围的调查，只保留已经收集到的观察，今天不动配置。", "调查已取消，已经记录的只读观察与待确认项保留。没有派发资源变更；后续如继续，应重新说明范围与当前时间窗。"),
                (f"帮我给晚班写个{project['name']}交接，标清哪些事情还没核实。", "晚班交接：固定样本含78条正常观察、6条拒绝和6条超时；这些是接收记录，不代表当前线上比例。已核对服务目录和共享依赖，当前告警是否恢复、业务状态是否一致仍待日志和指标补采。"),
                ("目前拿不到完整指标，可以告诉我缺哪些证据吗？别先给成功结论。", "证据不足，本轮调查未完成。需补齐同时间窗服务日志、依赖可用性与按请求标识去重的业务台账；三个来源应能互相核对。现有资料无法支持当前服务已恢复或处理方案有效的结论。"),
                (f"请复核{project['reviewObjective']}，重点检查影响范围、回滚和验收边界。", "建议范围已复核并保留独立审批历史示例。待执行方案必须包含目标环境、步骤、回滚及验收标准；本次摘要复核不授予资源写权限。展示库没有真实资源执行或模型回执。"))
            query, summary = scenarios[scenario]
            meta = {"syntheticDemonstration": True, "source": SOURCE, "runId": rid, "projectId": pid, "historyDay": day.isoformat(),
                    "phase": "INVESTIGATE", "evidenceLimitations": ["固定合成历史样本", "未调用真实模型"], "acceptanceStatus": "NOT_ASSESSED"}
            request = {"runId": rid, "sessionId": sid, "projectId": pid, "userId": owner,
                       "agentDefinitionId": selected["agentId"], "agentVersion": selected["version"], "query": query, "metadata": meta}
            events = []
            for seq, node, node_type, detail in (
                (1, "start", "START", "读取任务范围与绑定工作流"),
                (2, "investigate", "AGENT", "保留服务目录、只读观察与证据缺口"),
                (3, "report", "AGENT", summary),
                (4, "end", "END", "归档交接摘要与本轮结束状态")):
                event = {"eventType": "NODE_COMPLETED" if state == "SUCCEEDED" else "NODE_FAILED" if state == "FAILED" and seq == 3 else "NODE_CANCELED" if state == "CANCELED" and seq == 4 else "NODE_COMPLETED",
                         "nodeId": node, "nodeType": node_type, "agent": selected["agentId"], "source": "SHOWCASE",
                         "status": state if seq == 4 else "COMPLETED", "summary": detail, "content": detail,
                         "timestamp": iso, "payload": {**meta, "sequence": seq, "realModelCalled": False}}
                events.append(event)
                event_row("ai_ops_agent_node_trace", {"run_id": rid, "analysis_id": rid, "sequence_no": seq,
                    "event_type": event["eventType"], "node_id": node, "node_type": node_type,
                    "agent": selected["agentId"], "source": "SHOWCASE", "status": event["status"], "summary": detail,
                    "started_at": iso, "finished_at": iso, "duration_ms": 0, "payload_json": event["payload"], "create_time": stamp})
            add("ai_ops_graph_event_sequence", {"sequence_key": rid}, {"sequence_key": rid, "next_sequence": 4})
            response = {"sessionId": sid, "userId": owner, "agentId": selected["agentId"], "agentVersion": selected["version"],
                        "mode": "MULTI_TURN", "engine": "GRAPH", "content": summary, "events": events, "metadata": meta}
            add("ai_ops_chat_session", {"session_id": sid}, {"session_id": sid, "user_id": owner, "project_id": pid,
                "agent_id": selected["agentId"], "agent_binding_mode": "PINNED_VERSION", "agent_version": selected["version"],
                "agent_definition_hash": selected["definitionHash"], "title": day.isoformat() + " · " + project["name"] + " · " + titles[scenario],
                "mode": "MULTI_TURN", "engine": "GRAPH", "rag_enabled": 0, "status": "ACTIVE", "state_version": 1,
                "metadata": meta, "create_time": stamp, "last_active_at": stamp})
            for participant, role in ((owner, "OWNER"), (viewer, "OBSERVER"), (reviewer, "OBSERVER")):
                add("ai_ops_chat_session_participant", {"session_id": sid, "participant_user_id": participant}, {
                    "session_id": sid, "project_id": pid, "participant_user_id": participant, "participant_role": role,
                    "status": "ACTIVE", "state_version": 1, "added_by": owner, "create_time": stamp, "update_time": stamp})
            for seq, role, content in ((1, "user", query), (2, "assistant", summary),
                    (3, "user", "好的，请把未确认事项和下一步一起保留在交接里，先不要操作资源。"),
                    (4, "assistant", "已保留证据范围和待补采事项。下一步仍需实际指标、日志和业务台账交叉确认；需要变更时先准备可审核方案。")):
                event_row("ai_ops_chat_message", {"session_id": sid, "user_id": owner, "project_id": pid, "message_seq": seq,
                    "role": role, "content": content, "metadata": {**meta, "messageSeq": seq}, "turn_id": rid,
                    "capture_key": digest(sid + ":" + str(seq)), "create_time": stamp})
            add("ai_ops_agent_run", {"run_id": rid}, {"run_id": rid, "project_id": pid, "session_id": sid, "user_id": owner,
                "agent_id": selected["agentId"], "agent_version": selected["version"], "agent_definition_hash": selected["definitionHash"],
                "execution_harness": "PROJECT_PRE_APPROVAL", "status": state, "request_json": request, "response_json": response,
                "error_message": "观察资料不完整，保留待确认事项" if state == "FAILED" else None,
                # The durable Run reader uses JDBC getTimestamp even though
                # legacy columns are VARCHAR. Keep SQL storage compatible;
                # event/request timestamps retain the explicit UTC ISO value.
                "created_at": stamp, "updated_at": stamp, "duration_ms": 6400 + n * 530, "create_time": stamp, "update_time": stamp,
                "lease_token": "", "lease_expires_at": None, "cancel_requested": int(state == "CANCELED"), "cancel_reason": "缩小调查范围" if state == "CANCELED" else ""})
            add("ai_ops_analysis_task", {"run_id": rid}, {"run_id": rid, "project_id": pid, "trigger_source": "CHAT",
                "status": state, "request_json": request, "response_json": response,
                "error_message": "观察资料不完整" if state == "FAILED" else None, "created_at": iso, "updated_at": iso,
                "duration_ms": 6400 + n * 530, "create_time": stamp, "update_time": stamp})
            for participant, role in ((owner, "OWNER"), (viewer, "OBSERVER"), (reviewer, "OBSERVER")):
                add("ai_ops_agent_run_participant", {"run_id": rid, "participant_user_id": participant}, {
                    "run_id": rid, "project_id": pid, "session_id": sid, "participant_user_id": participant,
                    "participant_role": role, "status": "ACTIVE", "source": "SESSION_SNAPSHOT", "create_time": stamp})
            episode = "showcase-episode-" + key + f"-{n:02}"
            add("ai_ops_task_episode", {"episode_id": episode}, {"episode_id": episode, "project_id": pid,
                "session_id": sid, "goal": titles[scenario], "revision": 1, "outcome": "UNKNOWN",
                "verified_outcome_ref": "", "last_consolidated_revision": 1,
                "last_activity_ms": int(dt.datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp() * 1000), "create_time": stamp})
            context = {**meta, "goal": titles[scenario], "workflow": selected, "observations": summary}
            frozen = {**context, "turnSeq": 1, "endSeq": 4, "sourceRunRef": rid}
            add("ai_ops_task_episode_session", {"session_id": sid}, {"session_id": sid, "project_id": pid,
                "active_episode_id": "", "assigned_through_seq": 4, "update_time": stamp})
            add("ai_ops_task_episode_context", {"run_id": rid}, {"run_id": rid, "project_id": pid,
                "session_id": sid, "snapshot_json": context, "snapshot_hash": digest(context), "create_time": stamp})
            event_row("ai_ops_task_episode_turn", {"project_id": pid, "session_id": sid, "turn_seq": 1,
                "end_seq": 4, "source_run_ref": rid, "episode_id": episode, "episode_revision": 1,
                "classifier_revision": "showcase-manual-v1", "status": "ASSIGNED", "input_json": frozen,
                "input_hash": digest(frozen), "context_fidelity": "SYNTHETIC_HISTORY", "decision_json": {
                    "action": "CREATE", "goal": titles[scenario], "reason": "手工整理展示历史，未调用旁路模型", **meta},
                "attempts": 0, "lease_owner": "", "epoch": 0, "lease_until_ms": 0, "next_attempt_ms": 0,
                "last_error": "", "create_time": stamp, "update_time": stamp})
            # Native idle sweeps key on the exact generator identity, not the
            # last_consolidated_revision pointer. SOURCE_BLOCKED prevents an
            # archived synthetic Episode from entering the live model queue.
            generator = digest("task-episode-progress-luna-v1:gpt-6-luna:PROGRESS:UNKNOWN:v1")
            event_row("ai_ops_episode_consolidation_job", {"episode_id": episode, "revision": 1,
                "artifact_type": "PROGRESS", "generator_hash": generator, "trigger_reasons": "SHOWCASE_HISTORY",
                "status": "SOURCE_BLOCKED", "input_json": frozen, "input_hash": digest(frozen), "attempts": 0,
                "lease_owner": "", "epoch": 0, "lease_until_ms": 0, "next_attempt_ms": 0,
                "last_error": "SHOWCASE_SYNTHETIC_SOURCE_BLOCKED", "create_time": stamp, "update_time": stamp})
            manual_generator = digest(SOURCE + ":MANUAL_PROGRESS")
            add("ai_ops_episode_artifact", {"episode_id": episode, "revision": 1, "artifact_type": "PROGRESS", "generator_hash": manual_generator},
                {"episode_id": episode, "revision": 1, "artifact_type": "PROGRESS", "generator_hash": manual_generator,
                 "content": summary, "content_hash": digest(summary), "input_hash": digest(frozen), "outcome": "UNKNOWN", "create_time": stamp})
            if scenario == 7:
                approval = "showcase-workflow-approval-" + key + f"-{n:03}"
                add("ai_ops_workflow_approval", {"approval_id": approval}, {"approval_id": approval,
                    "run_id": rid, "project_id": pid, "node_id": "human-review",
                    "wait_token_hash": digest(approval + ":wait"), "approve_action_hash": digest(approval + ":approve"),
                    "reject_action_hash": digest(approval + ":reject"), "status": "APPROVED", "request_summary": "处理建议范围已确认；不授予资源写权限。",
                    "requested_at": stamp,
                    "expires_at": (dt.datetime.strptime(stamp, "%Y-%m-%d %H:%M:%S") + dt.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S"),
                    "decided_by": reviewer, "decided_at": stamp})
        schedules = [item for item in configured["schedules"] if item["projectId"] == pid]
        for n, day in enumerate(dates):
            schedule = schedules[n % 2]
            stamp = day.isoformat() + f" {pi:02}:00:00"
            event_row("ai_agent_task_execution", {"schedule_id": schedule["id"], "task_name": schedule["taskName"],
                "agent_id": agent["agentId"], "trigger_type": "MANUAL", "status": "FAILED" if n % 8 == 2 else "SUCCESS",
                "started_at": stamp, "ended_at": stamp, "input": project["inspectionQuestion"],
                "output": "值班摘要已整理：保留合成历史样本范围、重复接收统计和实时资料缺口。" if n % 8 != 2 else None,
                "error_message": "实时资料不足，巡检结论待确认" if n % 8 == 2 else None})
        signals = []
        for n in range(1, len(dates[::4]) * 2 + 1):
            signal_index = (n - 1) % 6
            iid = f"showcase-incident-{key}-{n:02}"
            day_index = ((n - 1) // 2) * 4
            rid = run_ids[day_index]
            stamp = dates[day_index].isoformat() + f" {pi:02}:{30 + ((n - 1) % 2) * 2:02}:00"
            service = project["services"][(n - 1) % 3]
            title = ("请求延迟升高", "依赖访问超时", "重复通知增多", "状态比对异常", "响应失败增多", "观察资料中断")[signal_index]
            state = ("INVESTIGATING", "ACKED", "RESOLVED", "REVIEWED", "MITIGATED", "OPEN")[signal_index]
            labels = {"projectId": pid, "environment": "test", "service": service["serviceId"], "source": SOURCE}
            meta = {"syntheticDemonstration": True, "source": SOURCE, "environment": "test", "rootCauseConfirmed": False}
            add("ai_ops_incident", {"incident_id": iid}, {"incident_id": iid, "project_id": pid,
                "title": service["name"] + " · " + title, "status": state, "severity": "WARN" if n % 3 else "CRITICAL",
                "service_name": service["serviceId"], "source_type": "SHOWCASE", "fingerprint": iid,
                "dedup_key": iid, "current_run_id": rid, "summary": "同时间窗存在相关异常，原始信号保留。事件归组不代表根因已经确认。",
                "labels_json": labels, "metadata_json": meta, "occurrence_count": 2 + n,
                "first_seen_at": stamp, "last_seen_at": stamp, "create_time": stamp, "update_time": stamp,
                "resolved_at": stamp if state in ("RESOLVED", "REVIEWED") else None, "reviewed_at": stamp if state == "REVIEWED" else None})
            add("ai_ops_incident_run", {"incident_id": iid, "run_id": rid}, {"incident_id": iid, "run_id": rid, "create_time": stamp})
            event_row("ai_ops_incident_timeline", {"incident_id": iid, "event_type": "NOTE", "title": "调查与交接摘要",
                "detail": "已保留不同症状、重复接收次数与证据缺口；恢复通知不自动关闭其他待核验信号。",
                "actor": owner, "ref_type": "RUN", "ref_id": rid, "payload_json": meta, "create_time": stamp})
            event_id = event_row("ai_ops_alert_trigger_event", {"project_id": pid, "rule_id": next((r["id"] for r in configured.get("alertRules", []) if r["projectId"] == pid), None), "rule_name": project["name"] + " · 值班告警",
                "source_type": "SHOWCASE", "status": "COMPLETED", "fingerprint": iid, "alert_name": title,
                "severity": "warning", "service_name": service["serviceId"], "run_id": rid,
                "run_status": ("SUCCEEDED", "SUCCEEDED", "FAILED", "SUCCEEDED", "CANCELED", "SUCCEEDED", "FAILED", "SUCCEEDED")[day_index % 8], "final_summary": "调查记录已归档，证据限制保留。",
                "completed_at": stamp, "labels_json": labels, "annotations_json": {"summary": title, "source": SOURCE},
                "payload_json": meta, "create_time": stamp})
            signals.append({"eventId": event_id, "incidentId": iid, "projectId": pid, "environment": "test",
                "entityId": service["serviceId"], "startedAt": stamp.replace(" ", "T") + "Z", "receivedAt": stamp.replace(" ", "T") + "Z",
                "identities": {"sharedResource": project["services"][0]["dependency"]},
                "title": service["name"] + " · " + title, "recovery": False, "source": SOURCE, "syntheticDemonstration": True})
        for index in range(len(signals) // 2):
            group = "showcase-correlation-" + key + "-" + str(index + 1)
            pair = signals[index * 2:index * 2 + 2]
            stamp = pair[0]["startedAt"].replace("T", " ").replace("Z", "")
            epoch = int(dt.datetime.fromisoformat(pair[0]["startedAt"].replace("Z", "+00:00")).timestamp())
            decision = {"join": True, "score": 0.86, "reason": "展示关联：同时间窗与共同依赖，根因仍未确认",
                        "evidenceRefs": [SOURCE], "policyVersion": "showcase-manual-v1", "syntheticDemonstration": True}
            add("ai_ops_alert_correlation_group", {"group_id": group}, {"group_id": group, "project_id": pid,
                "environment": "test", "anchor_json": pair[0], "anchor_epoch": epoch, "revision": 2,
                "created_at": stamp, "updated_at": stamp})
            for signal in pair:
                add("ai_ops_alert_correlation_member", {"group_id": group, "incident_id": signal["incidentId"]}, {
                    "group_id": group, "incident_id": signal["incidentId"], "signal_json": signal,
                    "decision_json": decision, "last_event_id": signal["eventId"], "occurrence_count": 3})
                add("ai_ops_alert_correlation_decision", {"event_id": signal["eventId"]}, {"event_id": signal["eventId"],
                    "group_id": group, "signal_json": signal, "decision_json": decision, "created_at": stamp})
        for n, day in enumerate(dates[::5], 1):
            state = ("DRAFT", "REVIEWING", "REJECTED", "LANDED")[(n - 1) % 4]
            run_number = (n - 1) * 5 + 1
            package = f"showcase-package-{key}-{n:02}"
            session = f"showcase-session-{key}-{run_number:02}"
            incident = f"showcase-incident-{key}-{((run_number - 1) // 4) * 2 + 1:02}"
            stamp = day.isoformat() + f" {pi:02}:40:00"
            objective = project["reviewObjective"] + (" · 只读证据补采", " · 测试资源验证", " · 范围需重新限定", " · 已归档处理示例")[(n - 1) % 4]
            metadata = {"syntheticDemonstration": True, "source": SOURCE, "realResourceExecuted": False}
            snapshot = {"packageId": package, "projectId": pid, "sessionId": session, "incidentId": incident,
                "packageType": "MCP_OPERATION_PACKAGE", "status": state, "version": 1, "objective": objective,
                "summary": "保留调查事实、影响范围和回滚边界。此记录是隔离展示历史，不是实际资源执行证明。",
                "riskLevel": "MEDIUM", "targetEnvironment": "test", "serviceId": project["services"][0]["serviceId"],
                "targetScope": {"projectId": pid, "environment": "test"}, "evidence": [metadata], "mcpSteps": [],
                "rollbackSteps": [], "verificationCriteria": {"status": "NOT_ASSESSED"}, "metadata": metadata}
            phash = digest(snapshot)
            snapshot["packageHash"] = phash
            add("ai_ops_change_package", {"package_id": package}, {"package_id": package, "session_id": session,
                "incident_id": incident, "project_id": pid, "package_type": "MCP_OPERATION_PACKAGE", "status": state,
                "version": 1, "package_hash": phash, "objective": objective, "summary": snapshot["summary"],
                "risk_level": "MEDIUM", "target_environment": "test", "service_id": snapshot["serviceId"],
                "evidence_json": [metadata], "target_scope_json": snapshot["targetScope"], "mcp_steps_json": [],
                "rollback_steps_json": [], "verification_criteria_json": snapshot["verificationCriteria"],
                "approved_version": 1 if state == "LANDED" else None,
                "approved_package_hash": phash if state == "LANDED" else None,
                "approved_snapshot_json": snapshot if state == "LANDED" else None,
                "approve_by": reviewer if state == "LANDED" else None, "approved_at": stamp if state == "LANDED" else None,
                "landing_result_json": {"syntheticDemonstration": True, "source": SOURCE,
                    "realResourceExecuted": False, "summary": "归档处理示例；没有实际资源执行回执。"} if state == "LANDED" else None,
                "create_by": owner, "create_time": stamp, "update_time": stamp})
            add("ai_ops_change_package_version", {"package_id": package, "version": 1}, {"package_id": package,
                "version": 1, "package_hash": phash, "status": state, "snapshot_json": snapshot,
                "change_summary": "展示历史初版", "created_by": owner, "create_time": stamp})
            eid = package + "-created"
            add("ai_ops_change_package_event", {"event_id": eid}, {"event_id": eid, "package_id": package,
                "event_type": "PACKAGE_CREATED", "actor": owner, "summary": "候选方案与证据边界已记录", "payload_json": metadata, "create_time": stamp})
            if state in ("REJECTED", "LANDED"):
                add("ai_ops_change_package_approval_record", {"approval_id": package + "-decision"}, {
                    "approval_id": package + "-decision", "package_id": package, "project_id": pid, "version": 1,
                    "package_hash": phash, "risk_level": "MEDIUM", "approver": reviewer, "actor_scope": "PROJECT_APPROVER",
                    "decision": "REJECTED" if state == "REJECTED" else "APPROVED", "admin_confirmation": 0,
                    "metadata_json": metadata, "create_time": stamp})
                if state == "LANDED":
                    approver = users["lead"]["userId"]
                    add("ai_ops_change_package_approval_record", {"approval_id": package + "-independent-decision"}, {
                        "approval_id": package + "-independent-decision", "package_id": package, "project_id": pid, "version": 1,
                        "package_hash": phash, "risk_level": "MEDIUM", "approver": approver, "actor_scope": "ADMIN",
                        "decision": "APPROVED", "admin_confirmation": 0, "metadata_json": metadata, "create_time": stamp})
        for n, document_name in enumerate(project["documents"]):
            document = h["ROOT"] / "scripts/fixtures/daily-workspace/knowledge" / document_name
            content = document.read_text()
            docid = "showcase-document-" + key + f"-{n + 1}"
            kb = "showcase-" + key + "-runbooks"
            meta = {"syntheticDemonstration": True, "source": SOURCE, "contentSha256": digest(content), "content": content}
            add("ai_ops_knowledge_document", {"document_id": docid}, {"document_id": docid, "kb_id": kb,
                "kb_scope": "PROJECT", "project_id": pid, "file_name": document_name, "display_name": content.splitlines()[0].lstrip("# "),
                "source_type": "SHOWCASE_MANUAL", "document_type": "MARKDOWN", "file_size": len(content.encode()),
                "parse_status": "PARSED", "vector_status": "NOT_INDEXED", "chunk_count": 1,
                "structure_preserved": 1, "ingestion_job_id": "", "metadata_json": meta})
            add("ai_ops_knowledge_chunk", {"chunk_id": docid + "-1"}, {"chunk_id": docid + "-1", "document_id": docid,
                "kb_id": kb, "kb_scope": "PROJECT", "project_id": pid, "chunk_index": 0,
                "chunk_strategy": "MANUAL_SHOWCASE", "content_preview": content,
                "parse_status": "READY", "vector_status": "NOT_INDEXED", "metadata_json": meta})
        for n, (title, content) in enumerate((
                ("交接偏好", "交接摘要按现象、证据、已确认事实、未确认事项和下一步组织，当前状态无法确认时直接说明。"),
                ("重复接收口径", "请求次数和去重后的业务数量分开统计。同一请求标识的重试不直接记为新业务。"),
                ("变更审核边界", "调查阶段保持只读。变更必须说明范围、回滚和验收标准，并经正常审核后再执行。")), 1):
            mid = f"showcase-memory-{key}-{n}"
            stamp = dates[(n - 1) * 28].isoformat() + f" {pi:02}:45:00"
            event_row("ai_ops_context_memory", {"memory_id": mid, "scope_type": "PROJECT", "scope_id": pid,
                "memory_type": "PROJECT_CONVENTION", "title": project["name"] + " · " + title, "summary": content,
                "content": content, "keywords": project["name"] + ",交接,只读", "status": "ACTIVE",
                "confidence": 0.6, "source_type": "SHOWCASE_MANUAL", "source_id": SOURCE, "source_message_hash": digest(content),
                "created_by": owner, "create_time": stamp, "update_time": stamp})
        for n, day in enumerate(dates[::7], 1):
            candidate = f"showcase-candidate-{key}-{n}"
            skill = next((item for item in configured.get("skills", []) if item["projectId"] == pid), {})
            changes = {"summary": "将同时间窗、请求去重和证据缺口补充到值班检查项" if n == 1 else "拆分现象归组与根因确认，避免扩大操作范围", "syntheticDemonstration": True, "source": SOURCE}
            add("ai_ops_skill_patch_candidate", {"candidate_id": candidate}, {"candidate_id": candidate,
                "candidate_hash": digest(candidate + SOURCE), "source_run_id": run_ids[(n - 1) * 7], "source_type": "SHOWCASE_HISTORY",
                "project_id": pid, "agent_id": agent["agentId"], "scope": "PROJECT", "target_skill_id": skill.get("skillId", ""),
                "patch_type": "UPDATE_SKILL", "risk_level": "LOW", "base_skill_version": skill.get("currentVersion") or 1,
                "base_skill_hash": skill.get("skillHash") or "", "context_bundle_hash": digest(changes),
                "evidence_refs_json": [{"source": SOURCE, "verified": False}], "changes_json": [{"section": "procedure", "key": "evidence-review", "value": {"steps": [changes["summary"]], "whenToUse": ["同时间窗的只读值班调查"], "stopConditions": ["缺少实时证据", "需要资源写权限"]}}],
                "eval_cases_json": [], "status": "REJECTED", "reason_code": "SHOWCASE_NOT_REAL_VERIFIED_SOURCE",
                "create_time": day.isoformat() + f" {pi:02}:50:00", "update_time": day.isoformat() + f" {pi:02}:50:00"})
            authored = {"candidateId": candidate, "patchType": "UPDATE_SKILL", "changes": [{"section": "procedure", "key": "evidence-review", "value": {"steps": [changes["summary"]], "whenToUse": ["只读值班调查"], "stopConditions": ["证据不足"]}}], "evalCases": [],
                "authoringReason": "手工归档的候选建议：未获得真实任务验收来源，不发布", "authoringSource": "SHOWCASE_MANUAL", "syntheticDemonstration": True, "source": SOURCE}
            patch = "showcase-patch-" + key + "-" + str(n)
            add("ai_ops_skill_evolution_patch", {"patch_id": patch}, {"patch_id": patch,
                "job_id": "showcase-skill-job-" + key + "-" + str(n), "run_id": run_ids[(n - 1) * 7], "project_id": pid,
                "target_skill_id": skill.get("skillId", ""), "decision": "SKIP", "patch_json": {
                    "content": h["canonical"](authored), "summary": {"source": SOURCE, "syntheticDemonstration": True}},
                "validation_json": {"status": "NOT_ASSESSED", "source": SOURCE, "syntheticDemonstration": True},
                "status": "SKIPPED", "applied_version": None, "skipped_reason": "SHOWCASE_NOT_REAL_VERIFIED_SOURCE",
                "create_time": day.isoformat() + f" {pi:02}:50:00", "update_time": day.isoformat() + f" {pi:02}:50:00"})
        for n, day in enumerate(dates[::7], 1):
            job = f"showcase-skill-job-{key}-{n}"
            add("ai_ops_skill_evolution_job", {"job_id": job}, {"job_id": job, "run_id": run_ids[(n - 1) * 7],
                "session_id": f"showcase-session-{key}-{(n - 1) * 7 + 1:02}", "project_id": pid, "agent_id": agent["agentId"],
                "trigger_reason": "SHOWCASE_HISTORY", "status": "COMPLETED" if n % 2 else "FAILED", "attempts": 1,
                "next_run_at": None, "last_error": "展示历史：来源证据不足，未发布自动进化版本" if n % 2 == 0 else None,
                "create_time": day.isoformat() + f" {pi:02}:50:00", "update_time": day.isoformat() + f" {pi:02}:50:00"})
    return rows


def table_sql(name):
    import re
    if not re.fullmatch(r"[a-z0-9_]+(?:\.[a-z0-9_]+)?", name):
        raise ValueError("Invalid fixed presentation table")
    return ".".join("`" + item + "`" for item in name.split("."))


def readback(h, rows):
    literal, sql, canonical = h["literal"], h["sql"], h["canonical"]
    found, missing = [], []
    for offset in range(0, len(rows), 50):
        batch = rows[offset:offset + 50]
        statements = []
        for index, row in enumerate(batch):
            where = " AND ".join(f"`{name}` <=> {literal(value)}" for name, value in row["key"].items())
            obj = ",".join(f"{literal(name)},`{name}`" for name in row["fields"])
            statements.append(f"SELECT JSON_OBJECT('ordinal',{index},'fields',JSON_OBJECT({obj})) FROM {table_sql(row['table'])} WHERE {where};")
        raw = sql("\n".join(statements))
        seen = {}
        for line in raw.splitlines():
            value = json.loads(line)
            index = value["ordinal"]
            if index in seen or not isinstance(index, int) or not 0 <= index < len(batch):
                raise ValueError("Ambiguous seeded identity; no rows changed")
            seen[index] = value["fields"]
        for index, row in enumerate(batch):
            if index not in seen:
                missing.append(row); continue
            actual = seen[index]
            for name, value in row["fields"].items():
                if isinstance(value, str) and isinstance(actual.get(name), str) and name.endswith(("_time", "_at")) and __import__("re").fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}", value) and __import__("re").fullmatch(__import__("re").escape(value) + r"\.0{1,6}", actual[name]):
                    actual[name] = value
            expected = {name: canonical(value) if isinstance(value, (dict, list)) else value for name, value in row["fields"].items()}
            if actual != expected:
                changed = [name for name in expected if actual.get(name) != expected[name]]
                raise ValueError(f"Existing presentation row differs: {row['table']} {list(row['key'].values())}, fields={changed}. Retained without overwrite")
            found.append(row)
    return found, missing


def run(h, apply=False):
    h["labels"]("mysql"); h["labels"]("backend")
    if h["sql"]("SELECT DATABASE();").strip() != "orbisops_showcase":
        raise ValueError("Synthetic history is forbidden in any other database")
    import importlib.util
    location = h["ROOT"] / "scripts/showcase-business.py"
    module_spec = importlib.util.spec_from_file_location("showcase_business", location)
    business = importlib.util.module_from_spec(module_spec); module_spec.loader.exec_module(business)
    business_result = business.run(h, type("HistoryReadback", (), {"readback": staticmethod(readback), "table_sql": staticmethod(table_sql), "digest": staticmethod(digest)}), apply=apply)
    if apply:
        configured = configure(h)
    else:
        configured = json.loads((h["PRIVATE"] / "catalog-receipt.json").read_text())
    rows = histories(h, configured)
    found, missing = readback(h, rows)
    sql_lines = []
    for row in missing:
        names = ",".join("`" + name + "`" for name in row["fields"])
        values = ",".join(h["literal"](value) for value in row["fields"].values())
        sql_lines.append(f"INSERT INTO `{row['table']}` ({names}) VALUES ({values});")
    sql_source = "-- Synthetic showcase history, not real operational evidence.\n-- Only orbisops_showcase; no UPDATE/DELETE/DROP/TRUNCATE.\nUSE orbisops_showcase;\nSTART TRANSACTION;\n" + "\n".join(sql_lines) + "\nCOMMIT;\n"
    if apply and missing:
        attempt_path = h["PRIVATE"] / ("history-attempt-" + digest(sql_source)[:12] + ".sql")
        if not attempt_path.exists(): h["private_write"](attempt_path, sql_source)
        h["sql"](sql_source)
    after, still_missing = readback(h, rows) if apply else (found, missing)
    counts = dict(Counter(row["table"] for row in rows))
    manifest = {"seedId": "orbisops-daily-workspace-v2", "source": SOURCE,
        "scope": "SYNTHETIC_PRESENTATION_ONLY_NOT_REAL_TEST_PASS", "database": "orbisops_showcase",
        "rowCount": len(rows), "tableCounts": counts, "rowsInserted": len(missing) if apply else 0,
        "rowsReused": len(found), "rowsReadBackExactly": len(after), "missingRows": len(still_missing),
        "manifestSha256": digest(rows), "historyStart": h["history_dates"]()[0].isoformat(), "historyEnd": h["history_dates"]()[-1].isoformat(), "historiesContainProviderReceipts": False,
        "historiesContainActiveRunLeases": False, "vectorRetrievalVerified": False,
        "historyRows": [{"table": row["table"], "key": row["key"], "sha256": digest(row["fields"])} for row in rows],
        "configuredNormally": configured, "businessReadback": business_result}
    if apply:
        import_path = h["PRIVATE"] / "history-seed.sql"
        if not import_path.exists():
            h["private_write"](import_path, sql_source)
        elif missing:
            # Retain the first complete import SQL; each repair is separately
            # named and still contains INSERT only for missing identities.
            h["private_write"](h["PRIVATE"] / ("history-repair-" + digest(sql_source)[:12] + ".sql"), sql_source)
        h["private_write"](h["PRIVATE"] / "history-manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", replace=True)
    manifest["status"] = "EXACT_SYNTHETIC_CONTENT_READBACK" if not still_missing else "MISSING_PRESENTATION_ROWS"
    # Print a compact manifest, not hundreds of messages or JSON fields.
    return {key: value for key, value in manifest.items() if key not in ("historyRows", "configuredNormally")}
