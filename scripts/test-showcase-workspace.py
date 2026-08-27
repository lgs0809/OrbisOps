#!/usr/bin/env python3
"""Offline seed-tool contract checks, not product/model/business acceptance."""
from pathlib import Path
import importlib.util
import json
import datetime as dt
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), ROOT / "scripts" / (name + ".py"))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


workspace, history = load("showcase-workspace"), load("showcase-history")


class ShowcaseContractTest(unittest.TestCase):
    def setUp(self):
        self.catalog = json.loads(workspace.CATALOG.read_text())
        self.users = {key: {"userId": "showcase-" + key} for key, *_ in workspace.ACCOUNTS}
        self.config = {"workflows": [{"projectId": p["projectId"], "agentId": w["agentId"], "version": 1,
                         "definitionHash": history.digest(w), "lifecycle": "PUBLISHED"}
                        for p in self.catalog["projects"] for w in history.workflows(p)],
                       "schedules": [{"id": i * 2 + n + 1, "projectId": p["projectId"], "taskName": p["name"] + str(n)}
                                     for i, p in enumerate(self.catalog["projects"]) for n in range(2)]}
        self.helper = {"ROOT": ROOT, "CATALOG": workspace.CATALOG, "accounts": lambda: self.users,
                       "literal": workspace.literal, "canonical": workspace.canonical,
                       "history_dates": lambda: [dt.date(2026, 6, 30) + dt.timedelta(days=n) for n in range(103)]}
        self.rows = history.histories(self.helper, self.config)

    def test_durable_run_timestamps_are_jdbc_readable(self):
        for row in self.rows:
            if row["table"] != "ai_ops_agent_run":
                continue
            run = row["fields"]
            for column in ("created_at", "updated_at"):
                parsed = dt.datetime.strptime(run[column], "%Y-%m-%d %H:%M:%S")
                self.assertEqual(parsed.isoformat() + "Z", run["response_json"]["events"][0]["timestamp"])

    def test_archived_approval_obeys_expiry_invariant(self):
        approvals = [r["fields"] for r in self.rows if r["table"] == "ai_ops_workflow_approval"]
        self.assertEqual(36, len(approvals))
        for approval in approvals:
            requested = dt.datetime.fromisoformat(approval["requested_at"])
            self.assertGreater(dt.datetime.fromisoformat(approval["expires_at"]), requested)
            self.assertGreaterEqual(dt.datetime.fromisoformat(approval["decided_at"]), requested)

    def test_fixed_isolation_and_disabled_model_default(self):
        plan = workspace.plan()
        self.assertEqual("orbisops-showcase-v2", plan["project"])
        self.assertEqual("http://127.0.0.1:18099", plan["addresses"]["api"])
        self.assertFalse(plan["acceptanceDatabaseUsed"])
        self.assertFalse(plan["modelCallsEnabledByDefault"])
        self.assertTrue(all(v.startswith("orbisops-showcase-v2_") for v in plan["volumes"]))
        self.assertEqual(3, plan["accounts"])
        self.assertEqual(1, sum(role == 'admin' for _, _, _, role in workspace.ACCOUNTS))

    def test_all_history_identities_are_deterministic_and_unique(self):
        self.assertEqual(self.rows, history.histories(self.helper, self.config))
        identities = [(r["table"], workspace.canonical(r["key"])) for r in self.rows]
        self.assertEqual(len(identities), len(set(identities)))

    def test_run_session_actor_and_workflow_references_match(self):
        sessions = {r["fields"]["session_id"]: r["fields"] for r in self.rows if r["table"] == "ai_ops_chat_session"}
        definitions = {w["agentId"]: w for w in self.config["workflows"]}
        runs = [r["fields"] for r in self.rows if r["table"] == "ai_ops_agent_run"]
        self.assertEqual(309, len(runs))
        for project in self.catalog["projects"]:
            self.assertEqual({day.isoformat() for day in self.helper["history_dates"]()},
                             {r["created_at"][:10] for r in runs if r["project_id"] == project["projectId"]})
        for run in runs:
            session = sessions[run["session_id"]]
            self.assertEqual(run["project_id"], session["project_id"])
            self.assertEqual(run["user_id"], session["user_id"])
            self.assertEqual(run["agent_definition_hash"], definitions[run["agent_id"]]["definitionHash"])
            self.assertIsNone(run["lease_expires_at"])
            self.assertEqual("", run["lease_token"])
            self.assertIn(run["status"], ("SUCCEEDED", "FAILED", "CANCELED"))
            self.assertTrue(run["request_json"]["metadata"]["syntheticDemonstration"])
            self.assertEqual(4, len(run["response_json"]["events"]))
            self.assertTrue(all(e["payload"]["syntheticDemonstration"] and not e["payload"]["realModelCalled"] for e in run["response_json"]["events"]))

    def test_conversations_are_natural_language_and_not_credential_copies(self):
        messages = [r["fields"] for r in self.rows if r["table"] == "ai_ops_chat_message"]
        self.assertEqual(1236, len(messages))
        for message in messages:
            self.assertFalse(message["content"].startswith(("{", "[")))
            self.assertNotIn("Bearer ", message["content"])
            self.assertNotIn("apiKey", message["content"])

    def test_correlation_preserves_distinct_incidents_and_same_window(self):
        groups = {r["fields"]["group_id"]: r["fields"] for r in self.rows if r["table"] == "ai_ops_alert_correlation_group"}
        members = [r["fields"] for r in self.rows if r["table"] == "ai_ops_alert_correlation_member"]
        self.assertEqual(78, len(groups)); self.assertEqual(156, len(members))
        for member in members:
            signal = member["signal_json"]
            anchor = groups[member["group_id"]]["anchor_json"]
            self.assertEqual(anchor["projectId"], signal["projectId"])
            self.assertEqual(anchor["startedAt"][:10], signal["startedAt"][:10])
            self.assertEqual("showcase-manual-v1", member["decision_json"]["policyVersion"])
            self.assertIn(history.SOURCE, member["decision_json"]["evidenceRefs"])

    def test_synthetic_landed_example_has_two_distinct_reviewers_and_no_real_receipt(self):
        packages = [r["fields"] for r in self.rows if r["table"] == "ai_ops_change_package" and r["fields"]["status"] == "LANDED"]
        decisions = [r["fields"] for r in self.rows if r["table"] == "ai_ops_change_package_approval_record"]
        self.assertEqual(15, len(packages))
        for package in packages:
            reviewers = {d["approver"] for d in decisions if d["package_id"] == package["package_id"] and d["decision"] == "APPROVED"}
            self.assertEqual(2, len(reviewers))
            self.assertFalse(package["landing_result_json"]["realResourceExecuted"])
            self.assertEqual(package["package_hash"], package["approved_package_hash"])

    def test_episode_sources_are_assigned_and_never_automatically_queued(self):
        turns = [r["fields"] for r in self.rows if r["table"] == "ai_ops_task_episode_turn"]
        guards = [r["fields"] for r in self.rows if r["table"] == "ai_ops_episode_consolidation_job"]
        runs = {r["fields"]["run_id"] for r in self.rows if r["table"] == "ai_ops_agent_run"}
        self.assertEqual(309, len(turns)); self.assertEqual(309, len(guards))
        for turn in turns:
            self.assertIn(turn["source_run_ref"], runs); self.assertEqual("ASSIGNED", turn["status"])
            self.assertEqual((1, 4), (turn["turn_seq"], turn["end_seq"]))
            self.assertEqual(0, turn["lease_until_ms"])
        for guard in guards:
            self.assertEqual("SOURCE_BLOCKED", guard["status"])
            self.assertEqual(history.digest("task-episode-progress-luna-v1:gpt-6-luna:PROGRESS:UNKNOWN:v1"), guard["generator_hash"])

    def test_synthetic_candidate_changes_are_arrays_and_never_publishable(self):
        candidates = [r["fields"] for r in self.rows if r["table"] == "ai_ops_skill_patch_candidate"]
        patches = [r["fields"] for r in self.rows if r["table"] == "ai_ops_skill_evolution_patch"]
        self.assertEqual(45, len(candidates)); self.assertEqual(45, len(patches))
        for candidate in candidates:
            self.assertEqual("REJECTED", candidate["status"]); self.assertIsInstance(candidate["changes_json"], list)
        for patch in patches:
            self.assertEqual("SKIPPED", patch["status"])
            self.assertTrue(json.loads(patch["patch_json"]["content"])["syntheticDemonstration"])

    def test_business_rows_cover_related_retries_and_failure_windows(self):
        business = load("showcase-business")
        for project in self.catalog["projects"]:
            rows = business.dataset(project, self.helper["history_dates"]())
            self.assertEqual(9286, len(rows))
            observations = [r["fields"] for r in rows if r["table"].endswith(".request_observation")]
            self.assertEqual(9270, len(observations)); self.assertEqual(7416, len({r["request_key"] for r in observations}))
            self.assertEqual({"OK": 8034, "TIMEOUT": 618, "REJECTED": 618}, {status: sum(r["outcome"] == status for r in observations) for status in ("OK", "TIMEOUT", "REJECTED")})
            for day in self.helper["history_dates"]():
                sample = [r for r in observations if r["occurred_at"].startswith(day.isoformat())]
                self.assertEqual(90, len(sample))
                self.assertEqual(72, len({r["request_key"] for r in sample}))

    def test_disabled_models_require_exact_image_pins(self):
        with self.assertRaisesRegex(ValueError, "exact local tested image"):
            workspace.init("orbisops/server:latest", "orbisops/web:latest")

    def test_sql_literal_has_no_executable_quotes_or_backslashes(self):
        escaped = workspace.literal("'; DROP TABLE admin_user; \\")
        self.assertTrue(escaped.startswith("CONVERT(0x"))
        self.assertNotIn("DROP", escaped)
        self.assertNotIn("'", escaped)

    def test_existing_matching_row_is_reused(self):
        row = self.rows[0]
        expected = {k: workspace.canonical(v) if isinstance(v, (dict, list)) else v for k, v in row["fields"].items()}
        helper = {**self.helper, "sql": lambda _: json.dumps({"ordinal": 0, "fields": expected}, ensure_ascii=False) + "\n"}
        found, missing = history.readback(helper, [row])
        self.assertEqual([row], found); self.assertEqual([], missing)

    def test_only_zero_fraction_datetime_format_is_normalized(self):
        row={"table":"ai_ops_workflow_approval","key":{"approval_id":"sample"},"fields":{"approval_id":"sample","requested_at":"2026-10-08 10:20:00"}}
        helper={**self.helper,"sql":lambda _:json.dumps({"ordinal":0,"fields":{"approval_id":"sample","requested_at":"2026-10-08 10:20:00.000000"}})+"\n"}
        self.assertEqual([row],history.readback(helper,[row])[0])
        helper["sql"]=lambda _:json.dumps({"ordinal":0,"fields":{"approval_id":"sample","requested_at":"2026-10-08 10:20:00.001000"}})+"\n"
        with self.assertRaisesRegex(ValueError,"Retained without overwrite"):history.readback(helper,[row])

    def test_content_suffix_is_not_treated_as_datetime_format(self):
        row={"table":"ai_ops_chat_session","key":{"session_id":"sample"},"fields":{"session_id":"sample","title":"用户编辑"}}
        helper={**self.helper,"sql":lambda _:json.dumps({"ordinal":0,"fields":{"session_id":"sample","title":"用户编辑.000"}})+"\n"}
        with self.assertRaisesRegex(ValueError,"Retained without overwrite"):history.readback(helper,[row])

    def test_empty_literal_is_a_valid_mysql_hex_string(self):
        self.assertEqual("CONVERT(X'' USING utf8mb4)",workspace.literal(""))

    def test_patch_job_candidate_references_share_one_run(self):
        jobs={r["fields"]["job_id"]:r["fields"] for r in self.rows if r["table"]=="ai_ops_skill_evolution_job"}
        candidates={r["fields"]["candidate_id"]:r["fields"] for r in self.rows if r["table"]=="ai_ops_skill_patch_candidate"}
        for row in self.rows:
            if row["table"]!="ai_ops_skill_evolution_patch":continue
            patch=row["fields"];cid=json.loads(patch["patch_json"]["content"])["candidateId"]
            self.assertEqual(patch["run_id"],jobs[patch["job_id"]]["run_id"])
            self.assertEqual(patch["run_id"],candidates[cid]["source_run_id"])

    def test_edited_existing_row_rejected_without_overwrite(self):
        row = self.rows[0]
        expected = {k: workspace.canonical(v) if isinstance(v, (dict, list)) else v for k, v in row["fields"].items()}
        expected["title"] = "用户自己修改的标题"
        helper = {**self.helper, "sql": lambda _: json.dumps({"ordinal": 0, "fields": expected}, ensure_ascii=False) + "\n"}
        with self.assertRaisesRegex(ValueError, "Retained without overwrite"):
            history.readback(helper, [row])


if __name__ == "__main__":
    unittest.main()
