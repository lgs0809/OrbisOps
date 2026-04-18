package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.Map;
import java.util.function.Supplier;

/** Resolves the Agent snapshot that already owns the current Work Session. */
final class OpsPreparationAgentResolver {

    private final OpsAgentDefinitionQueryGateway agentDefinitionRegistry;
    private final Supplier<ProjectDefinitionApplicationService> projectDefinitionSupplier;

    OpsPreparationAgentResolver(
            OpsAgentDefinitionQueryGateway agentDefinitionRegistry,
            Supplier<ProjectDefinitionApplicationService> projectDefinitionSupplier) {
        if (agentDefinitionRegistry == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REGISTRY_REQUIRED");
        }
        this.agentDefinitionRegistry = agentDefinitionRegistry;
        this.projectDefinitionSupplier = projectDefinitionSupplier;
    }

    OpsAgentDefinition resolve(String projectId, Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String frozenAgentId = firstText(
                nestedText(safe.get("agentSnapshot"), "agentId"),
                nestedText(safe.get("agentRunExecutionContext"), "agentId"),
                text(safe.get("agentDefinitionId")),
                text(safe.get("agentId")),
                projectDefaultAgentId(projectId));
        if (frozenAgentId.isBlank()) {
            throw new IllegalArgumentException("PREPARATION_AGENT_SNAPSHOT_REQUIRED");
        }
        rejectExecutionOverride(safe, frozenAgentId);
        OpsAgentDefinition definition = agentDefinitionRegistry.resolve(frozenAgentId);
        verifyFrozenVersion(safe, definition);
        return definition;
    }


    private void rejectExecutionOverride(Map<String, Object> request, String frozenAgentId) {
        String requested = firstText(
                text(request.get("preparationGraphId")),
                text(request.get("preparationAgentId")));
        if (!requested.isBlank() && !requested.equals(frozenAgentId)) {
            throw new SecurityException("PREPARATION_AGENT_OVERRIDE_FORBIDDEN");
        }
    }

    private void verifyFrozenVersion(
            Map<String, Object> request,
            OpsAgentDefinition definition) {
        String requestedVersion = firstText(
                nestedText(request.get("agentSnapshot"), "agentVersion"),
                nestedText(request.get("agentRunExecutionContext"), "agentVersion"),
                text(request.get("agentVersion")));
        if (requestedVersion.isBlank() || definition == null || definition.getVersion() == null) return;
        if (!requestedVersion.equals(String.valueOf(definition.getVersion()))) {
            throw new SecurityException("PREPARATION_AGENT_VERSION_MISMATCH");
        }
    }

    private String projectDefaultAgentId(String projectId) {
        ProjectDefinitionApplicationService service = projectDefinitionSupplier == null
                ? null
                : projectDefinitionSupplier.get();
        if (service == null) return "";
        try {
            return text(service.defaultAgentId(projectId));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String nestedText(Object source, String key) {
        if (!(source instanceof Map<?, ?> map)) return "";
        return text(map.get(key));
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) if (!text(value).isBlank()) return text(value);
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
