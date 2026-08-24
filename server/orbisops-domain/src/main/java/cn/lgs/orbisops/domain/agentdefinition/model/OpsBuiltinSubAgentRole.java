package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.List;
import java.util.Locale;

/** Built-in sub-agent roles and their fail-closed default tool boundaries. */
public enum OpsBuiltinSubAgentRole {

    MAIN_ASSISTANT(List.of(
            "Skill", "UseProjectSkill", "knowledge_retrieve",
            "project_mcp_*", "mcp_tool_catalog_*", "enable_mcp_tool_*",
            "prometheus_query", "elasticsearch_search",
            "ValidateCodeCandidate", "code_read", "code_grep", "code_glob", "code_lsp",
            "code_enter_worktree", "code_edit", "code_write", "code_bash",
            "code_compute_diff", "code_commit_repair", "code_exit_worktree",
            "PrepareChangePackage", "QueryChangePackageStatus", "ManageInspectionTask", "tool_result_*")),
    EVIDENCE_EXPLORER(List.of(
            "Skill", "UseProjectSkill", "knowledge_retrieve", "project_mcp_*", "mcp_tool_catalog_*",
            "enable_mcp_tool_*", "prometheus_query", "elasticsearch_search", "tool_result_*")),
    CODE_INVESTIGATOR(List.of(
            "UseProjectSkill", "code_read", "code_grep", "code_glob", "code_lsp", "tool_result_*")),
    REPAIR_WORKER(List.of(
            "UseProjectSkill", "ValidateCodeCandidate", "code_read", "code_grep", "code_glob",
            "code_lsp", "code_enter_worktree", "code_edit", "code_write", "code_bash",
            "code_compute_diff", "code_commit_repair", "code_exit_worktree", "tool_result_*")),
    VALIDATION_RUNNER(List.of(
            "UseProjectSkill", "code_read", "code_grep", "code_glob", "code_bash",
            "code_compute_diff", "project_mcp_*", "mcp_tool_catalog_*", "enable_mcp_tool_*",
            "tool_result_*")),
    EVIDENCE_CRITIC(List.of("UseProjectSkill", "tool_result_*")),
    CHANGE_PACKAGE_COMPOSER(List.of("PrepareChangePackage", "QueryChangePackageStatus", "tool_result_*"));

    private final List<String> defaultAllowedTools;

    OpsBuiltinSubAgentRole(List<String> defaultAllowedTools) {
        this.defaultAllowedTools = List.copyOf(defaultAllowedTools);
    }

    public List<String> defaultAllowedTools() {
        return defaultAllowedTools;
    }

    public static OpsBuiltinSubAgentRole parse(String value) {
        if (value == null || value.trim().isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("GENERAL".equals(normalized)) {
            return MAIN_ASSISTANT;
        }
        if ("DATA_AGENT".equals(normalized)
                || "INVESTIGATOR".equals(normalized)) {
            return EVIDENCE_EXPLORER;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "不支持的内置子 Agent 角色：" + value,
                    exception);
        }
    }
}
