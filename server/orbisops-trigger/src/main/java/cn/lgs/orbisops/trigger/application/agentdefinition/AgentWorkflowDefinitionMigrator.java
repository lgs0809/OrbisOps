package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Central compatibility migration from legacy open workflow DTOs to schema v1. */
public final class AgentWorkflowDefinitionMigrator {

    public void migrate(OpsAgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_WORKFLOW_DEFINITION_REQUIRED");
        }
        int schemaVersion = definition.getSchemaVersion() == null
                ? 0
                : definition.getSchemaVersion();
        if (schemaVersion < 0 || schemaVersion > AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "WORKFLOW_SCHEMA_VERSION_UNSUPPORTED:" + schemaVersion);
        }
        if (schemaVersion == 0) {
            migrateLegacyV0(definition);
        } else {
            normalizeV1(definition);
        }
        definition.setSchemaVersion(AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION);
    }

    private void migrateLegacyV0(OpsAgentDefinition definition) {
        for (OpsWorkflowNode node : nodes(definition)) {
            String type = normalizeLegacyNodeType(node.getType());
            if ("START".equals(type) || "END".equals(type)) {
                node.setType(type);
                continue;
            }
            if ("ROUTER".equals(type)) {
                configureRouter(node);
                continue;
            }
            String mode = inferNodeMode(node, type);
            node.setType("AGENT");
            configureAgent(node, mode);
        }
    }

    private void normalizeV1(OpsAgentDefinition definition) {
        for (OpsWorkflowNode node : nodes(definition)) {
            String publishedType = AgentWorkflowNodeType.normalizePublishedName(node.getType());
            AgentWorkflowNodeType.fromPublishedName(publishedType);
            node.setType(publishedType);
            if ("ROUTER".equals(publishedType)) {
                configureRouter(node);
                continue;
            }
            if (Set.of("START", "END").contains(publishedType)) {
                continue;
            }
            String mode = inferNodeMode(node, publishedType);
            if (!StringUtils.hasText(node.getMode())) {
                node.setMode(mode);
            }
            Map<String, Object> config = mutableConfig(node);
            config.putIfAbsent("mode", mode);
            if (Set.of("AGENT", "SUB_AGENT", "AGENTSCOPE", "PLAN", "REVIEW", "REFLECT")
                    .contains(publishedType)) {
                config.putIfAbsent("role", defaultRoleForMode(mode));
            }
            if ("review".equals(mode)) {
                config.putIfAbsent("reviewMode", "immediate");
            }
            node.setConfig(config);
        }
    }

    private void configureRouter(OpsWorkflowNode node) {
        node.setType("ROUTER");
        node.setMode(null);
        if (!StringUtils.hasText(node.getOutputKey())) {
            node.setOutputKey("selectedRoutes");
        }
        Map<String, Object> config = mutableConfig(node);
        config.remove("mode");
        config.putIfAbsent("routeMode", "multi");
        config.putIfAbsent("inputKey", "plan");
        node.setConfig(config);
    }

    private void configureAgent(OpsWorkflowNode node, String mode) {
        node.setMode(mode);
        Map<String, Object> config = mutableConfig(node);
        config.putIfAbsent("mode", mode);
        config.putIfAbsent("role", defaultRoleForMode(mode));
        if ("review".equals(mode)) {
            config.putIfAbsent("reviewMode", "immediate");
        }
        node.setConfig(config);
    }

    private List<OpsWorkflowNode> nodes(OpsAgentDefinition definition) {
        return Optional.ofNullable(definition.getNodes()).orElse(List.of()).stream()
                .filter(node -> node != null)
                .toList();
    }

    private Map<String, Object> mutableConfig(OpsWorkflowNode node) {
        return new LinkedHashMap<>(Optional.ofNullable(node.getConfig()).orElse(Map.of()));
    }

    private String inferNodeMode(OpsWorkflowNode node, String type) {
        String configured = firstText(
                node.getMode(),
                configText(node.getConfig(), "mode"),
                configText(node.getConfig(), "agentMode"));
        String mode = configured.trim().toLowerCase().replace("-", "_");
        if (Set.of("direct", "llm", "react", "auto", "plan", "review").contains(mode)) {
            return mode;
        }
        if ("PLAN".equals(type)) return "plan";
        if ("REVIEW".equals(type) || "REFLECT".equals(type)) return "review";
        if (Set.of("SUB_AGENT", "EXECUTE", "AGENTSCOPE", "RAG", "MCP",
                "TOOL_CALL", "KNOWLEDGE_RETRIEVAL", "TOOL", "SKILL")
                .contains(type)) {
            return "react";
        }
        return "auto";
    }

    private String defaultRoleForMode(String mode) {
        if ("plan".equals(mode)) return "main_planner";
        if ("react".equals(mode)) return "data_agent";
        if ("review".equals(mode)) return "reviewer";
        return "general";
    }

    private String configText(Map<String, Object> config, String key) {
        if (config == null || !config.containsKey(key)) return "";
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeLegacyNodeType(String type) {
        return StringUtils.hasText(type)
                ? type.trim().toUpperCase().replace("-", "_")
                : "AGENT";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }
}
