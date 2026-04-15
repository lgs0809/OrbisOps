---
name: change-package-preparer
description: Turn verified investigation, worktree, artifact, and TEST-environment evidence into a reviewable production ChangePackage task book.
whenToUse: ["Verified investigation and TEST/worktree evidence are ready for a production ChangePackage task book", "已有真实排查和测试环境证据，需要整理变更方案提交审批"]
whenNotToUse: ["Evidence is insufficient or contradictory", "直接执行生产变更、跳过审批或改变 Landing 权限"]
---

# ChangePackage Preparer

Use this Skill when the current Agent or Workflow has enough evidence to prepare, revise, or submit a production change for human approval.

## Evidence first

1. Do not create a ChangePackage from speculation. Establish the target, current production state, desired state, and why the proposed change addresses the problem.
2. For code changes, use the controlled worktree flow first: pin `baseCommit`, modify/test in the isolated worktree, compute the diff, create a verified repair commit, and capture `changedFiles`, `diffHash`, `repairCommit`, artifact digest, and test evidence.
3. When the project provides DEV/TEST MCP resources, use the non-production environment for the required integration/deployment verification before proposing PROD execution.
4. Keep realtime PROD observations, TEST verification, code/worktree evidence, and stable RAG/SOP guidance distinct.
5. If evidence is insufficient or contradictory, continue investigation or explain the gap instead of inventing a production change.

## Package construction

Treat ChangePackage as the approved production task book, not as a second per-tool RBAC list. Capture at least:
- objective and reason for the production change;
- target project/environment and affected systems/resources;
- current-state evidence and desired state;
- verified source/worktree facts such as `baseCommit`, `repairCommit`, `diffHash`, and changed files when applicable;
- artifact/image/config identity and `artifactDigest` when applicable;
- TEST/DEV verification evidence and remaining production assumptions;
- recommended execution plan and important ordering/dependencies;
- acceptance/verification criteria;
- rollback or recovery guidance and known risks.

Individual operations may still be recorded for clarity, audit, and repeatability, but do not turn their tool names or exact arguments into Landing authorization ACLs. Landing may use additional project-bound production tools, diagnostics, retries, or a different safe step order while the approved plan remains materially the same.

Use `change_package_create` for a new package and `change_package_revise` for an existing one. Review the stored package with `change_package_detail` before submission. Call `change_package_submit_review` only when the task book, verification, and rollback guidance are coherent.

## Approval and Landing boundary

Creating or submitting a package grants no production write authority. Human Approval confirms the frozen ChangePackage version/hash. After approval, `start_landing` starts the platform-owned Landing ReAct Runtime with project-scoped PROD_FULL capability.

Landing should continue autonomously through ordinary execution failures, diagnostics, retries, and step reordering while the approved plan remains valid. If continuing would materially change the approved plan itself—for example adding a new database migration, changing the production target or deployment strategy, materially increasing risk, or invalidating rollback—Landing must return `NEEDS_REPLAN`. The package then enters `REVISING`, produces a new version, and goes through approval again.
