package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Builds the typed runtime resource context for agent, node and agent-scope execution. */
@Component
public final class OpsRuntimeResourceContextFactory {

    private final OpsRuntimeSkillResolver skillResolver;
    private final OpsRuntimeMcpResolver mcpResolver;

    public OpsRuntimeResourceContextFactory(
            OpsRuntimeSkillResolver skillResolver,
            OpsRuntimeMcpResolver mcpResolver) {
        if (skillResolver == null) {
            throw new IllegalArgumentException("RUNTIME_SKILL_RESOLVER_REQUIRED");
        }
        if (mcpResolver == null) {
            throw new IllegalArgumentException("RUNTIME_MCP_RESOLVER_REQUIRED");
        }
        this.skillResolver = skillResolver;
        this.mcpResolver = mcpResolver;
    }

    public OpsRuntimeResourceContext agent(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return OpsRuntimeResourceContext.builder()
                .definition(definition)
                .request(request)
                .executionContext(new OpsAgentRunExecutionContextFactory().resolve(request))
                .events(events)
                .eventSink(eventSink)
                .projectId(firstText(
                        request == null ? null : request.getProjectId(),
                        definition.getProjectId()))
                .modelId(firstText(
                        request == null ? null : request.getModelId(),
                        definition.getModelId()))
                .ragEnabled(firstNonNull(
                        definition.getRagEnabled(),
                        request == null ? null : request.getRagEnabled()))
                .knowledgeBaseId(firstText(
                        definition.getKnowledgeBaseId(),
                        request == null ? null : request.getKnowledgeBaseId()))
                .changePackageEnabled(definition.getChangePackageEnabled())
                .skillNames(copySet(definition.getSkills()))
                .mcpIds(copySet(definition.getMcpIds()))
                .executionTargetIds(copySet(definition.getExecutionTargetIds()))
                .mcpServers(copyList(definition.getMcpServers()))
                .build();
    }

    public OpsRuntimeResourceContext node(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        LinkedHashSet<String> skills = copySet(definition.getSkills());
        skills.addAll(optionalList(node.getSkills()));
        LinkedHashSet<String> mcpIds = copySet(node.getMcpIds());
        LinkedHashSet<String> executionTargetIds = copySet(
                definition.getExecutionTargetIds());
        executionTargetIds.addAll(optionalList(node.getExecutionTargetIds()));
        String projectId = firstText(
                request == null ? null : request.getProjectId(),
                definition.getProjectId());
        if (Boolean.TRUE.equals(booleanConfig(
                node.getConfig(), "inheritProjectCapabilities"))) {
            inheritProjectCapabilities(projectId, skills, mcpIds);
        } else if (Boolean.TRUE.equals(booleanConfig(
                node.getConfig(), "inheritProjectMcpCapabilities"))) {
            inheritProjectMcpCapabilities(projectId, mcpIds);
        }
        return OpsRuntimeResourceContext.builder()
                .definition(definition)
                .node(node)
                .request(request)
                .executionContext(new OpsAgentRunExecutionContextFactory().resolve(request))
                .events(events)
                .eventSink(eventSink)
                .projectId(projectId)
                // A published node's explicit model is part of its compiled resource identity.
                // The chat selector supplies a default only for nodes without a model binding.
                .modelId(firstText(
                        node.getModelId(),
                        request == null ? null : request.getModelId(),
                        definition.getModelId()))
                .ragEnabled(firstNonNull(
                        node.getRagEnabled(),
                        definition.getRagEnabled(),
                        request == null ? null : request.getRagEnabled()))
                .knowledgeBaseId(firstText(
                        node.getKnowledgeBaseId(),
                        definition.getKnowledgeBaseId(),
                        request == null ? null : request.getKnowledgeBaseId()))
                .repairEnabled(node.getRepairEnabled())
                .changePackageEnabled(firstNonNull(
                        node.getChangePackageEnabled(),
                        definition.getChangePackageEnabled()))
                .skillNames(skills)
                .mcpIds(mcpIds)
                .executionTargetIds(executionTargetIds)
                .mcpServers(copyList(node.getMcpServers()))
                .build();
    }

    public OpsRuntimeResourceContext agentScope(
            OpsAgentDefinition definition,
            OpsAgentScopeConfig config,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        LinkedHashSet<String> skills = copySet(definition.getSkills());
        skills.addAll(optionalList(config.getSkills()));
        LinkedHashSet<String> mcpIds = copySet(config.getMcpIds());
        LinkedHashSet<String> executionTargetIds = copySet(
                definition.getExecutionTargetIds());
        executionTargetIds.addAll(optionalList(config.getExecutionTargetIds()));
        String projectId = firstText(
                request == null ? null : request.getProjectId(),
                definition.getProjectId());
        if (Boolean.TRUE.equals(config.getInheritProjectCapabilities())) {
            inheritProjectCapabilities(projectId, skills, mcpIds);
        }
        return OpsRuntimeResourceContext.builder()
                .definition(definition)
                .agentScope(config)
                .request(request)
                .executionContext(new OpsAgentRunExecutionContextFactory().resolve(request))
                .events(events)
                .eventSink(eventSink)
                .projectId(projectId)
                .modelId(firstText(
                        config.getModelId(),
                        request == null ? null : request.getModelId(),
                        definition.getModelId()))
                .ragEnabled(firstNonNull(
                        config.getRagEnabled(),
                        definition.getRagEnabled(),
                        request == null ? null : request.getRagEnabled()))
                .knowledgeBaseId(firstText(
                        config.getKnowledgeBaseId(),
                        definition.getKnowledgeBaseId(),
                        request == null ? null : request.getKnowledgeBaseId()))
                .repairEnabled(config.getRepairEnabled())
                .changePackageEnabled(firstNonNull(
                        config.getChangePackageEnabled(),
                        definition.getChangePackageEnabled()))
                .skillNames(skills)
                .mcpIds(mcpIds)
                .executionTargetIds(executionTargetIds)
                .mcpServers(copyList(config.getMcpServers()))
                .build();
    }

    private void inheritProjectCapabilities(
            String projectId,
            Set<String> skills,
            Set<String> mcpIds) {
        if (!StringUtils.hasText(projectId)) {
            return;
        }
        skills.addAll(skillResolver.enabledProjectSkillIds(projectId));
        inheritProjectMcpCapabilities(projectId, mcpIds);
    }

    private void inheritProjectMcpCapabilities(
            String projectId,
            Set<String> mcpIds) {
        if (!StringUtils.hasText(projectId)) return;
        mcpIds.addAll(mcpResolver.enabledProjectMcpIds(projectId));
    }

    private Boolean booleanConfig(Map<String, Object> config, String key) {
        if (config == null || !config.containsKey(key)) {
            return null;
        }
        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private Boolean firstNonNull(Boolean... values) {
        for (Boolean value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private LinkedHashSet<String> copySet(List<String> values) {
        return new LinkedHashSet<>(optionalList(values));
    }

    private <T> ArrayList<T> copyList(List<T> values) {
        return new ArrayList<>(optionalList(values));
    }

    private <T> List<T> optionalList(List<T> values) {
        return Optional.ofNullable(values).orElse(List.of());
    }
}
