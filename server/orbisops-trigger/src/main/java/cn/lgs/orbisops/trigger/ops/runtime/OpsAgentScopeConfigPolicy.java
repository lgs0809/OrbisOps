package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Selects and projects AgentScope configuration without building runtime agents. */
final class OpsAgentScopeConfigPolicy {

    List<OpsAgentScopeConfig> configs(OpsAgentDefinition definition,
                                      OpsAgentChatRequest request) {
        List<OpsAgentScopeConfig> configured = Optional.ofNullable(
                definition.getAgentscopeAgents()).orElse(List.of());
        if (!configured.isEmpty()) {
            return List.copyOf(configured);
        }
        return Optional.ofNullable(definition.getNodes()).orElse(List.of()).stream()
                .filter(node -> "AGENTSCOPE".equals(normalizeEngine(node.getSubEngine()))
                        || "AGENTSCOPE".equals(normalizeType(node.getType())))
                .map(node -> configFromNode(definition, node))
                .toList();
    }

    OpsAgentScopeConfig defaultConfig(OpsAgentDefinition definition) {
        return OpsAgentScopeConfig.builder()
                .agentId(definition.getAgentId())
                .name(definition.getName())
                .instruction(definition.getInstruction())
                .outputKey("agent_0")
                .ragEnabled(definition.getRagEnabled())
                .knowledgeBaseId(definition.getKnowledgeBaseId())
                .inheritProjectCapabilities(true)
                .repairEnabled(true)
                .changePackageEnabled(definition.getChangePackageEnabled())
                .mcpServers(definition.getMcpServers())
                .mcpIds(definition.getMcpIds())
                .skills(definition.getSkills())
                .modelId(definition.getModelId())
                .build();
    }

    int maxToolRounds(OpsAgentDefinition definition,
                      OpsAgentScopeConfig config,
                      OpsAgentChatRequest request) {
        int maxIterations = Optional.ofNullable(config.getMaxIterations())
                .orElse(Optional.ofNullable(
                        definition.getDefaultSubAgentMaxIterations()).orElse(6));
        OpsAgentRunRequestDTO runRequest = OpsRuntimeToolContributionSupport.analysisRequest(request);
        OpsBuiltinSubAgentRole role = OpsBuiltinSubAgentRole.parse(config.getRole());
        if (runRequest != null
                && runRequest.getSubAgentMaxIterations() != null
                && role != null
                && role != OpsBuiltinSubAgentRole.MAIN_ASSISTANT) {
            maxIterations = runRequest.getSubAgentMaxIterations();
        }
        boolean hasMcpTools = (config.getMcpIds() != null
                && !config.getMcpIds().isEmpty())
                || (config.getMcpServers() != null
                && !config.getMcpServers().isEmpty());
        if (hasMcpTools) maxIterations = Math.max(maxIterations, 6);
        return Math.max(1, Math.min(maxIterations, 20));
    }

    private OpsAgentScopeConfig configFromNode(
            OpsAgentDefinition definition,
            OpsWorkflowNode node) {
        return OpsAgentScopeConfig.builder()
                .agentId(node.getAgent())
                .name(firstText(node.getAgent(), node.getNodeId()))
                .instruction(firstText(
                        node.getInstruction(), definition.getInstruction()))
                .outputKey(StringUtils.hasText(node.getOutputKey())
                        ? node.getOutputKey()
                        : node.getNodeId())
                .outputContract(OpsRuntimePromptAssembler.nodeOutputContract(node))
                .ragEnabled(firstNonNull(
                        node.getRagEnabled(), definition.getRagEnabled()))
                .knowledgeBaseId(firstText(
                        node.getKnowledgeBaseId(), definition.getKnowledgeBaseId()))
                .role(configText(node, "role"))
                .maxDepth(intConfig(node, "maxDepth", 1))
                .allowedToolNames(stringConfigList(node, "allowedToolNames"))
                .inheritProjectCapabilities(Boolean.parseBoolean(firstText(
                        configText(node, "inheritProjectCapabilities"), "false")))
                .changePackageStatusEnabled(node.getConfig() != null && Boolean.TRUE.equals(node.getConfig().get("changePackageStatusEnabled")))
                .repairEnabled(Boolean.TRUE.equals(node.getRepairEnabled()))
                .changePackageEnabled(firstNonNull(
                        node.getChangePackageEnabled(),
                        definition.getChangePackageEnabled()))
                .mcpServers(node.getMcpServers())
                .mcpIds(node.getMcpIds())
                .skills(node.getSkills())
                .build();
    }

    private String normalizeEngine(String engine) {
        if (!StringUtils.hasText(engine)) return "GRAPH";
        String normalized = engine.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.contains("AGENTSCOPE")) return "AGENTSCOPE";
        if (normalized.contains("HYBRID")) return "HYBRID";
        if (normalized.contains("CHAT")) return "CHAT";
        return "GRAPH";
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type)
                ? type.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                : "CHAT";
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null
                || node.getConfig() == null
                || !node.getConfig().containsKey(key)) {
            return null;
        }
        Object value = node.getConfig().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int intConfig(OpsWorkflowNode node, String key, int fallback) {
        if (node == null || node.getConfig() == null) return fallback;
        Object value = node.getConfig().get(key);
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException(
                        "节点配置 " + key + " 必须是整数");
            }
        }
        return fallback;
    }

    private List<String> stringConfigList(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null) return List.of();
        Object value = node.getConfig().get(key);
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            }
            return List.copyOf(values);
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return List.of(String.valueOf(value).trim());
        }
        return List.of();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

}
