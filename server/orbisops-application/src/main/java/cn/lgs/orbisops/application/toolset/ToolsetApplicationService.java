package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;
import cn.lgs.orbisops.domain.toolset.service.ToolsetPolicy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Application owner of Toolset catalog governance and execution entry. */
public final class ToolsetApplicationService {

    private final ToolsetCatalogPort catalogPort;
    private final ToolExecutionPort executionPort;
    private final ToolsetAuditPort auditPort;
    private final ToolsetPolicy policy;

    public ToolsetApplicationService(
            ToolsetCatalogPort catalogPort,
            ToolExecutionPort executionPort,
            ToolsetAuditPort auditPort) {
        this(catalogPort, executionPort, auditPort, new ToolsetPolicy());
    }

    ToolsetApplicationService(
            ToolsetCatalogPort catalogPort,
            ToolExecutionPort executionPort,
            ToolsetAuditPort auditPort,
            ToolsetPolicy policy) {
        if (catalogPort == null) throw new IllegalArgumentException("TOOLSET_CATALOG_PORT_REQUIRED");
        if (executionPort == null) throw new IllegalArgumentException("TOOL_EXECUTION_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("TOOLSET_AUDIT_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("TOOLSET_POLICY_REQUIRED");
        this.catalogPort = catalogPort;
        this.executionPort = executionPort;
        this.auditPort = auditPort;
        this.policy = policy;
    }

    public List<ToolsetDefinition> builtIn() {
        return catalogPort.listBuiltIn();
    }

    public List<ToolsetDefinition> custom(String projectId) {
        return catalogPort.listCustom(policy.projectId(projectId));
    }

    public List<ToolsetDefinition> effective(String projectId, String userId) {
        return catalogPort.listEffective(
                policy.projectId(projectId),
                required(userId, "TOOLSET_USER_ID_REQUIRED"));
    }

    public ToolsetDefinition registerCustom(
            String projectId,
            Map<String, Object> request,
            String actor) {
        String project = policy.projectId(projectId);
        String operator = required(actor, "TOOLSET_ACTOR_REQUIRED");
        String generatedId = "custom-" + UUID.randomUUID();
        Map<String, Object> normalized = policy.customToolset(project, request, generatedId);
        String toolsetId = String.valueOf(normalized.get("toolsetId"));
        ToolsetDefinition before = catalogPort.listCustom(project).stream()
                .filter(item -> toolsetId.equals(item.toolsetId()))
                .findFirst()
                .orElse(null);
        ToolsetDefinition result = catalogPort.registerCustom(
                project,
                normalized,
                operator);
        auditPort.record(
                project,
                before == null ? "register-custom" : "update-custom",
                result.toolsetId(),
                before,
                result);
        return result;
    }

    public Map<String, Object> setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        return setEnabledOutcome(projectId, toolsetId, enabled, actor).view();
    }

    public ToolsetActivationOutcome setEnabledOutcome(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        String project = policy.projectId(projectId);
        String id = policy.toolsetId(toolsetId);
        String operator = required(actor, "TOOLSET_ACTOR_REQUIRED");
        ToolsetDefinition before = catalogPort.listCustom(project).stream()
                .filter(item -> id.equals(item.toolsetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("TOOLSET_NOT_FOUND:" + id));
        ToolsetDefinition updated = catalogPort.setEnabled(
                project,
                id,
                enabled,
                operator);
        ToolsetActivationOutcome outcome = new ToolsetActivationOutcome(project, updated);
        auditPort.record(
                project,
                enabled ? "enable" : "disable",
                id,
                before,
                updated);
        return outcome;
    }

    public Map<String, Object> refreshMcp(
            String projectId,
            String toolsetId,
            String actor) {
        return refreshMcpOutcome(projectId, toolsetId, actor).view();
    }

    public ToolsetRefreshOutcome refreshMcpOutcome(
            String projectId,
            String toolsetId,
            String actor) {
        String project = policy.projectId(projectId);
        String id = policy.toolsetId(toolsetId);
        ToolsetRefreshOutcome result = catalogPort.refreshMcp(
                project,
                id,
                required(actor, "TOOLSET_ACTOR_REQUIRED"));
        auditPort.record(project, "refresh-mcp", id, null, result);
        return result;
    }

    public Map<String, Object> execute(Map<String, Object> request, String actor) {
        String operator = required(actor, "TOOL_EXECUTION_ACTOR_REQUIRED");
        return executionPort.execute(policy.execution(request, operator), operator);
    }

    private String required(String input, String error) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
