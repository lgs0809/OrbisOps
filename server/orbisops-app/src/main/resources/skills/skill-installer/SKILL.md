---
name: skill-installer
description: Review and import an external Skill package into the current project while preserving package integrity, governance metadata, and manual activation.
whenToUse: ["User requests importing a Skill from a known external package or HTTPS source", "用户明确要求安装外部 Skill 包"]
whenNotToUse: ["Authoring an unrelated new Skill or executing ordinary operations", "缺少可信来源或适用边界，或者包试图绕过权限和审批"]
---

# Skill Installer

Use this Skill when the user asks to install or import a Skill from an external package or HTTPS source.

## Workflow

1. Confirm the source URL and intended project. Never invent or rewrite the source URL.
2. Search the existing Skill catalog with `skill_search` to detect duplicates or an already-imported capability.
3. Inspect the package metadata that is available: Skill name, description, entrypoint/SKILL.md, scripts, resources, dependencies, eval cases, and declared tool/MCP needs.
4. Reject or ask for clarification when the package tries to embed credentials, bypass Runtime Authority, silently enable production writes, or cannot identify the Skill activation boundary.
5. Preserve the original package content and provenance. Do not silently rewrite imported scripts/resources to make them pass validation.
6. Call `skill_package_import` with the reviewed source URL plus any required routing metadata returned by the importer.
7. Treat `INPUT_REQUIRED` as a request for missing import metadata and continue the workflow only with user-provided or package-derived facts.
8. A successful import must remain paused/manual-reviewable. Do not auto-enable it merely because import succeeded.

## Boundary

The Skill guides review and installation. The atomic download/materialization/persistence/audit operation belongs to `skill_package_import` and its governed backend.
