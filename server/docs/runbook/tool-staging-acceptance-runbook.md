# Tool Staging 故障注入与证据验收 Runbook

## 1. 目的

本 Runbook 用于阶段 648 的真实 Staging 验收。它只接受客户相似、可识别的 Staging 环境，不接受本地参考 Server、Fake Endpoint、单元测试结果或人工口头确认替代现场证据。

验收目标不是证明“接口能返回成功”，而是证明下列闭环同时成立：

```text
精确授权
→ 受控执行
→ 不确定结果对账
→ 外部 Receipt
→ Audit / Journal / Trusted Proof 一致
→ 故障下 fail-closed
→ 可验证补偿或人工恢复
```

## 2. 完成标准

阶段 648 只有同时满足以下条件才可标记完成：

1. 九个强制场景全部在真实 Staging 执行并标记 `PASSED`；
2. 每个场景提交规定数量的脱敏证据文件和 SHA-256；
3. 需要外部结果的场景必须提供真实 `externalReceiptIds`；
4. 操作人与复核人必须不同，复核人必须在 Run 完成后提交 `APPROVED` 与不少于 20 字符的独立复核声明；
5. 校验命令必须从部署流水线或受控变更记录传入预期 Environment ID、Build Version 和 Source Revision，不能只相信 Manifest 自述；
6. `scripts/ops-platform/tool-staging-evidence-check.mjs` 校验通过，并保存其输出的 `manifestSha256`；
7. 复核人确认环境、版本、Project、Provider、Tool 与证据包一致；
8. 未发现凭据、Token、私钥、口令或未脱敏业务数据进入证据包。

校验通过只说明证据包结构、完整性和必要事实满足门禁，不替代复核人对业务结果真实性的判断。

## 3. 强制场景

### 3.1 `mysql_readonly_account`

验证真实 MySQL 只读账号：

- `SHOW GRANTS FOR CURRENT_USER()` 不包含写入或管理权限；
- SELECT 可在精确库表 allowlist 内执行；
- UPDATE、DELETE、INSERT、DROP、TRUNCATE 由数据库权限实际拒绝，而不是仅由 MCP 文本 Guard 拒绝；
- 越权库表查询被 MCP 或数据库拒绝。

最低证据：

- 脱敏后的 `SHOW GRANTS`；
- 写操作拒绝结果与数据库错误码。

### 3.2 `redis_readonly_acl`

验证真实 Redis ACL 用户：

- `ACL GETUSER` 只包含所需读命令和 Key Pattern；
- GET/TTL/受控 SCAN 在允许前缀内可用；
- SET、DEL、EVAL、CONFIG、FLUSHDB 由 Redis ACL 实际拒绝；
- 越权 Key Pattern 被拒绝。

最低证据：

- 脱敏后的 ACL 配置；
- 写命令和越权 Key 的拒绝结果。

### 3.3 `mcp_timeout_unknown_reconciliation`

验证请求已发出但调用方超时的情况：

- 平台不得把超时直接声明为业务失败或成功；
- Landing/Tool Execution 进入 `UNKNOWN` 或等价待对账状态；
- 平台只调用 `get_operation_receipt(executionKey)` 对账，不重放写 Tool；
- 最终状态与外部 Receipt 一致。

最低证据：平台超时日志、外部 Receipt 查询结果。必须提供外部 Receipt ID。

### 3.4 `mcp_crash_restart_receipt_recovery`

验证 Provider 在状态与 Receipt 提交后、响应返回前崩溃：

- 调用方看到连接中断或未知结果；
- Provider 重启后可按原 executionKey 查询同一 Receipt；
- 重投相同命令返回同一 Receipt，不产生第二次业务变更；
- 当前业务状态与 Receipt 中版本一致。

最低证据：崩溃/重启记录、重启后的 Receipt 与状态查询。必须提供外部 Receipt ID。

### 3.5 `exact_rollout_and_approval_expiry`

验证精确 rollout 与审批授权：

- 非 allowlist Project 被拒绝；
- 非 allowlist Tool 被拒绝；
- approvalId 必须精确绑定 Project、Metric/业务对象、Actor 与 Operation；
- 过期审批被拒绝；
- `*` 通配符不得启用。

最低证据：允许路径和至少两类拒绝路径。必须提供成功路径外部 Receipt ID。

### 3.6 `emergency_stop`

验证平台侧和 Provider 侧紧急停止：

- 开启 emergency stop 后，新副作用执行被拒绝；
- 已完成 executionKey 的 Receipt 查询仍可用；
- 已完成命令的幂等重投只返回旧 Receipt；
- 只读状态查询仍可用；
- 关闭 emergency stop 必须经过受控配置变更。

最低证据：开关前后执行结果、已完成 Receipt 查询结果。必须提供外部 Receipt ID。

### 3.7 `audit_journal_proof_receipt_reconciliation`

对同一次执行核对四类权威记录：

- Tool Execution / Audit；
- Landing Journal；
- Trusted Proof；
- 外部 Provider Receipt。

至少核对：

```text
projectId
packageId / packageVersion / packageHash
operationId
executionKey
actor
approvalId
providerId
receiptId
resultHash / outputHash
finalStatus
```

四类记录不得出现执行键、Receipt、哈希或最终状态分叉。

最低证据：Audit、Journal、Trusted Proof、外部 Receipt 各一份。必须提供外部 Receipt ID。

### 3.8 `business_cas_backup_restore`

验证外部业务平台：

- expected value/version 正确时只迁移一个版本；
- expected value 或 version 漂移时拒绝覆盖；
- Provider 状态和 Receipt 持久化可经服务重启恢复；
- Staging 备份可恢复到隔离目标；
- 恢复后状态与备份点、Receipt 事实一致。

最低证据：CAS 结果、备份产物元数据、隔离恢复后的校验结果。必须提供外部 Receipt ID。

### 3.9 `compensation_failure_manual_recovery`

验证补偿失败不是被伪装成成功：

- compensation 使用独立 approvalId 和 executionKey；
- 状态漂移时补偿 CAS 被拒绝；
- 平台保留原执行成功事实与补偿失败事实；
- 进入明确的人工处理状态；
- 人工恢复后产生新的权威记录和 Receipt；
- 不允许覆盖或删除原始 Audit、Journal、Proof、Receipt。

最低证据：补偿失败、人工处理记录、最终恢复记录。必须提供补偿与恢复相关外部 Receipt ID。

## 4. 证据包目录

推荐结构：

```text
staging-tool-acceptance-<runId>/
├── manifest.json
└── evidence/
    ├── mysql_readonly_account/
    ├── redis_readonly_acl/
    ├── mcp_timeout_unknown_reconciliation/
    ├── mcp_crash_restart_receipt_recovery/
    ├── exact_rollout_and_approval_expiry/
    ├── emergency_stop/
    ├── audit_journal_proof_receipt_reconciliation/
    ├── business_cas_backup_restore/
    └── compensation_failure_manual_recovery/
```

证据文件必须：

- 位于 `manifest.json` 同目录或其子目录；
- 不使用绝对路径、`..` 或符号链接；
- 非空且不超过 50 MiB；
- 在 Manifest 中登记准确 SHA-256；
- 记录 `capturedAt`、`mediaType` 和用途说明；
- 在验收开始、结束时间窗口内采集；
- 完成凭据和业务敏感数据脱敏。

## 5. Manifest 最小结构

```json
{
  "schemaVersion": 1,
  "acceptanceType": "REAL_STAGING",
  "synthetic": false,
  "environment": {
    "name": "STAGING",
    "environmentId": "customer-like-staging-a",
    "customerLike": true
  },
  "run": {
    "runId": "tool-staging-20260804-001",
    "startedAt": "2026-08-04T01:00:00Z",
    "completedAt": "2026-08-04T02:00:00Z",
    "operator": "operator-a",
    "reviewer": "reviewer-b",
    "buildVersion": "staging-build-20260804",
    "sourceRevision": "0123456789abcdef0123456789abcdef01234567",
    "projectId": "staging-project-1",
    "providerId": "customer-ops-staging-mcp",
    "toolsetId": "ops.alert-threshold.write",
    "toolName": "update_alert_threshold"
  },
  "review": {
    "status": "APPROVED",
    "reviewedAt": "2026-08-04T02:15:00Z",
    "attestation": "Independently verified the deployed identity and all scenario evidence."
  },
  "scenarios": [
    {
      "id": "mcp_timeout_unknown_reconciliation",
      "status": "PASSED",
      "observedAt": "2026-08-04T01:20:00Z",
      "observedOutcome": "Timeout entered UNKNOWN and reconciled by receipt query without write replay.",
      "externalReceiptIds": ["provider-receipt-001"],
      "evidence": [
        {
          "path": "evidence/mcp_timeout_unknown_reconciliation/platform-log.json",
          "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
          "mediaType": "application/json",
          "description": "Redacted platform UNKNOWN and reconciliation log",
          "capturedAt": "2026-08-04T01:20:00Z"
        }
      ]
    }
  ]
}
```

实际 Manifest 必须包含九个场景，并达到各场景最低证据数量。示例中的哈希和标识仅展示结构，不能直接用于验收。

## 6. 生成文件哈希

macOS：

```bash
shasum -a 256 evidence/path/to/file.json
```

Linux：

```bash
sha256sum evidence/path/to/file.json
```

证据文件落盘后不得再编辑；任何修改都必须重新计算哈希并重新复核。

## 7. 执行门禁

从仓库根目录执行：

```bash
STAGING_TOOL_EVIDENCE_MANIFEST=/absolute/path/to/manifest.json \
STAGING_TOOL_EXPECTED_ENVIRONMENT_ID=customer-like-staging-a \
STAGING_TOOL_EXPECTED_BUILD_VERSION=staging-build-20260804 \
STAGING_TOOL_EXPECTED_SOURCE_REVISION=0123456789abcdef0123456789abcdef01234567 \
make tool-staging-evidence-check
```

三个 `EXPECTED_*` 值必须来自部署流水线、镜像元数据或受控变更记录，而不是从待校验 Manifest 中复制。

或直接执行：

```bash
node scripts/ops-platform/tool-staging-evidence-check.mjs \
  --manifest /absolute/path/to/manifest.json \
  --expected-environment-id customer-like-staging-a \
  --expected-build-version staging-build-20260804 \
  --expected-source-revision 0123456789abcdef0123456789abcdef01234567 \
  --json
```

校验输出中的 `manifestSha256` 应写入验收记录；Manifest 或任一证据文件发生变化后，必须重新校验和复核。

典型拒绝原因：

```text
STAGING_EVIDENCE_SYNTHETIC_FORBIDDEN
STAGING_EVIDENCE_SCENARIO_MISSING
STAGING_EVIDENCE_SCENARIO_UNKNOWN
STAGING_EVIDENCE_INDEPENDENT_REVIEW_REQUIRED
STAGING_EVIDENCE_REVIEW_REQUIRED
STAGING_EVIDENCE_REVIEW_NOT_APPROVED
STAGING_EVIDENCE_EXPECTED_ENVIRONMENT_MISMATCH
STAGING_EVIDENCE_EXPECTED_BUILD_MISMATCH
STAGING_EVIDENCE_EXPECTED_SOURCE_REVISION_MISMATCH
STAGING_EVIDENCE_EXTERNAL_RECEIPT_REQUIRED
STAGING_EVIDENCE_FILE_DUPLICATE
STAGING_EVIDENCE_FILE_HASH_MISMATCH
STAGING_EVIDENCE_SYMLINK_FORBIDDEN
STAGING_EVIDENCE_SECRET_FIELD_FORBIDDEN
STAGING_EVIDENCE_SECRET_MATERIAL_DETECTED
```

## 8. 安全要求

- 不把账号口令、API Key、Authorization Header、Cookie、私钥或完整连接串写入 Manifest；
- 文本证据中的常见凭据形式会被自动拒绝；
- 数据库结果只保留证明权限和状态所需字段；
- 用户、手机号、订单、业务配置等敏感值必须掩码；
- 外部 Receipt ID 可以保留，但 Receipt Payload 中的敏感字段必须脱敏；
- 原始证据应保存在受控审计存储中，仓库只保留脱敏副本或校验摘要。

## 9. 结论边界

本地参考 MCP Server、Java 集成测试和证据门禁均属于 Staging 前置能力。它们显著降低现场验收的不确定性，但不能替代真实 MySQL/Redis ACL、真实外部 Provider、真实网络故障和真实业务平台恢复演练。

在真实证据包通过门禁并完成独立复核之前，阶段 648 必须保持 `IN_PROGRESS`，不得声明生产验收完成。
