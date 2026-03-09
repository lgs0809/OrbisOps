package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the canonical Agent identity/capability snapshot used by runs and approvals. */
public final class OpsAgentSnapshotFactory {

    public AgentSnapshot fromRequest(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        String modelProfile = firstText(
                metadataText(request, "modelProfileId"),
                request == null ? null : request.getModelId(),
                definition.getModelId());
        return create(definition, modelProfile);
    }

    public AgentSnapshot fromDefinition(OpsAgentDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        return create(definition, definition.getModelId());
    }

    private AgentSnapshot create(
            OpsAgentDefinition definition,
            String modelProfile) {
        String promptHash = OpsRuntimeHashing.canonicalHash(Map.of(
                "instruction", firstText(definition.getInstruction(), "")));
        return new AgentSnapshot(
                definition.getAgentId(),
                definition.getVersion(),
                definition.getDefinitionHash(),
                promptHash,
                modelProfile,
                tools(definition),
                copy(definition.getMcpIds()),
                copy(definition.getSkills()),
                knowledge(definition));
    }

    private Set<String> tools(OpsAgentDefinition definition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        optional(definition.getAgentscopeAgents()).stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(config -> optional(config.getAllowedToolNames()).stream())
                .map(this::text)
                .filter(value -> !value.isBlank())
                .forEach(result::add);
        optional(definition.getNodes()).stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsWorkflowNode::getConfig)
                .filter(java.util.Objects::nonNull)
                .map(config -> config.get("allowedToolNames"))
                .filter(List.class::isInstance)
                .map(value -> (List<?>) value)
                .flatMap(List::stream)
                .map(this::text)
                .filter(value -> !value.isBlank())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private Set<String> knowledge(OpsAgentDefinition definition) {
        String knowledgeBaseId = text(definition.getKnowledgeBaseId());
        return knowledgeBaseId.isBlank() ? Set.of() : Set.of(knowledgeBaseId);
    }

    private Set<String> copy(List<String> source) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        optional(source).stream().map(this::text)
                .filter(value -> !value.isBlank()).forEach(values::add);
        return Set.copyOf(values);
    }

    private <T> List<T> optional(List<T> source) {
        return source == null ? List.of() : source;
    }

    private String metadataText(OpsAgentChatRequest request, String key) {
        return request == null || request.getMetadata() == null
                ? ""
                : text(request.getMetadata().get(key));
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
