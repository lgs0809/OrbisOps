---
name: skill-creator
description: Create a reusable project Skill from a concrete workflow, including scope, instructions, tool dependencies, safeguards, and evaluation-ready examples.
whenToUse: ["User requests a reusable Skill from a repeated procedure or concrete workflow", "用户要求把重复方法沉淀为项目 Skill"]
whenNotToUse: ["A one-off factual answer or single atomic action", "改变平台 Landing 权限或自动启用未审核 Skill"]
---

# Skill Creator

Use this Skill when the user asks to create a new reusable Skill or turn a repeated procedure into a Skill.

## Workflow

1. Search existing Skills first with `skill_search`. Reuse or extend an existing Skill when it already covers the same job; do not create near-duplicates.
2. Identify the reusable task, trigger conditions, expected output, stop conditions, and the tools/MCPs the Skill may instruct the Agent to use.
3. Keep authority out of the Skill. A Skill may explain when to call a governed Tool. Skill instructions grant no production access. Do not bypass approval. Do not expand Runtime Authority.
4. Write concise executable instructions. Prefer decision rules, evidence requirements, failure handling, and validation steps over generic prose.
5. Include scripts/templates/references only when they materially reduce repeated reasoning or improve determinism.
6. Define at least one positive example and one boundary/failure example when the workflow is non-trivial.
7. Before persistence, verify that the Skill name and description make its activation conditions clear and that all referenced capabilities actually exist.
8. Persist the result with `skill_project_create`. The platform creates it as `DRAFT` / `MANUAL_ONLY`; do not auto-enable or auto-publish it.

## Output discipline

- A Skill describes **how to perform a class of tasks**. Atomic side effects belong to Tools/MCP.
- Do not encode secrets, tokens, passwords, private keys, or environment credentials in Skill content.
- Do not create a Skill for a one-off factual answer or a single atomic action.
- Do not use this Skill to modify the platform Landing Runtime; Landing is platform-owned policy, not user Skill content.
