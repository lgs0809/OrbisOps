package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Executes a published child Workflow as a deterministic durable child Run. */
final class OpsSubWorkflowNodeExecutor {

    private static final int MAX_DEPTH = 4;

    private final OpsAgentDefinitionQueryGateway definitions;
    private final ObjectProvider<OpsChatApplicationService> chatProvider;

    OpsSubWorkflowNodeExecutor(
            OpsAgentDefinitionQueryGateway definitions,
            ObjectProvider<OpsChatApplicationService> chatProvider) {
        if (definitions == null || chatProvider == null) {
            throw new IllegalArgumentException("SUB_WORKFLOW_EXECUTOR_DEPENDENCIES_REQUIRED");
        }
        this.definitions = definitions;
        this.chatProvider = chatProvider;
    }

    Map<String, Object> execute(
            OpsAgentDefinition parentDefinition,
            OpsWorkflowNode node,
            OpsAgentChatRequest parentRequest,
            Map<String, Object> state) {
        if (node == null || !"SUB_WORKFLOW".equalsIgnoreCase(text(node.getType()))) {
            throw new IllegalArgumentException("SUB_WORKFLOW_NODE_REQUIRED");
        }
        String workflowId = required(node.getAgent(), "SUB_WORKFLOW_ID_REQUIRED");
        if (parentDefinition != null && workflowId.equals(parentDefinition.getAgentId())) {
            throw new IllegalArgumentException("SUB_WORKFLOW_SELF_REFERENCE_FORBIDDEN");
        }
        String projectId = required(
                parentRequest == null ? null : parentRequest.getProjectId(),
                "SUB_WORKFLOW_PROJECT_REQUIRED");
        String parentRunId = required(
                parentRequest == null ? null : parentRequest.getRunId(),
                "SUB_WORKFLOW_PARENT_RUN_REQUIRED");
        String nodeId = required(node.getNodeId(), "SUB_WORKFLOW_NODE_ID_REQUIRED");
        Map<String, Object> parentMetadata = parentRequest == null || parentRequest.getMetadata() == null
                ? Map.of()
                : parentRequest.getMetadata();
        int depth = integer(parentMetadata.get("subWorkflowDepth"), 0);
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("SUB_WORKFLOW_MAX_DEPTH_EXCEEDED");
        }

        // The child Run identity must not depend on LATEST_PUBLISHED resolution. If the
        // parent crashes after the child starts and a newer child version is published,
        // recovery must find the already-created child instead of launching a second Run.
        String childKey = CanonicalObjectHasher.sha256(Map.of(
                "parentRunId", parentRunId,
                "nodeId", nodeId,
                "workflowId", workflowId));
        String suffix = childKey.substring(0, 16);
        String childRunId = childIdentity(parentRunId, suffix);
        String parentSessionId = text(parentRequest.getSessionId());
        String childSessionBase = parentSessionId.isBlank() ? parentRunId : parentSessionId;
        String childSessionId = childIdentity(childSessionBase, suffix);
        OpsChatApplicationService chat = chatProvider.getObject();

        Map<String, Object> existing = existingRun(chat, childRunId, projectId);
        if (!existing.isEmpty()) {
            if (!workflowId.equals(text(existing.get("agent_id")))) {
                throw new IllegalStateException("SUB_WORKFLOW_CHILD_RUN_IDENTITY_CONFLICT");
            }
            if ("SUCCEEDED".equals(text(existing.get("status")))) {
                return output(node, existing, workflowId, childRunId, true);
            }
            throw new IllegalStateException(
                    "SUB_WORKFLOW_CHILD_RUN_NOT_REUSABLE:" + text(existing.get("status")));
        }

        ExecutionVersionPolicy policy = ExecutionVersionPolicy.parse(
                config(node, "versionPolicy"), ExecutionVersionPolicy.LATEST_PUBLISHED);
        Integer requestedVersion = policy == ExecutionVersionPolicy.PINNED_VERSION
                ? positiveInteger(
                        configObject(node, "version"),
                        "SUB_WORKFLOW_PINNED_VERSION_REQUIRED")
                : null;
        OpsAgentDefinition childDefinition = definitions.resolveForProject(
                workflowId, requestedVersion, false, projectId);
        if (childDefinition == null
                || AgentDefinitionKind.parse(childDefinition.getDefinitionKind())
                != AgentDefinitionKind.SPECIALIZED_WORKFLOW) {
            throw new IllegalArgumentException("SUB_WORKFLOW_PUBLISHED_WORKFLOW_REQUIRED");
        }
        if (childDefinition.getVersion() == null || childDefinition.getVersion() <= 0
                || text(childDefinition.getDefinitionHash()).isBlank()) {
            throw new IllegalStateException("SUB_WORKFLOW_DEFINITION_IDENTITY_REQUIRED");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "SUB_WORKFLOW");
        metadata.put("parentRunId", parentRunId);
        metadata.put("parentNodeId", nodeId);
        metadata.put(
                "parentWorkflowId",
                parentDefinition == null ? "" : text(parentDefinition.getAgentId()));
        metadata.put(
                "parentWorkflowVersion",
                parentDefinition == null ? null : parentDefinition.getVersion());
        metadata.put(
                "parentWorkflowDefinitionHash",
                parentDefinition == null ? "" : text(parentDefinition.getDefinitionHash()));
        metadata.put("subWorkflowDepth", depth + 1);
        metadata.put("subWorkflowId", workflowId);
        metadata.put("subWorkflowVersion", childDefinition.getVersion());
        metadata.put("subWorkflowDefinitionHash", childDefinition.getDefinitionHash());
        // Resolve LATEST_PUBLISHED once, then pin the exact published version into the
        // durable child session. A later publish must never move an in-flight child.
        metadata.put("agentBindingMode", "PINNED_VERSION");
        Object trustedPrincipal = parentMetadata.get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL);
        if (trustedPrincipal != null) {
            metadata.put(OpsTrustedRequestMetadata.AUTH_PRINCIPAL, trustedPrincipal);
        }

        OpsAgentChatRequest child = OpsAgentChatRequest.builder()
                .runId(childRunId)
                .sessionId(childSessionId)
                .userId(parentRequest.getUserId())
                .projectId(projectId)
                .agentDefinitionId(childDefinition.getAgentId())
                .agentVersion(childDefinition.getVersion())
                .agentDefinition(childDefinition)
                .query(input(node, parentRequest, state))
                .mode("WORKFLOW")
                .metadata(metadata)
                .build();
        chat.chat(child, text(parentRequest.getUserId()));
        // A returned answer can describe a failed execution. Only its committed Run
        // status and frozen identity authorize completing this parent node.
        Map<String, Object> completed = existingRun(chat, childRunId, projectId);
        if (!"SUCCEEDED".equals(text(completed.get("status")))) {
            throw new IllegalStateException("SUB_WORKFLOW_CHILD_RUN_NOT_SUCCEEDED:" + text(completed.get("status")));
        }
        if (!workflowId.equals(text(completed.get("agent_id")))
                || !String.valueOf(childDefinition.getVersion()).equals(text(completed.get("agent_version")))
                || !childDefinition.getDefinitionHash().equals(text(completed.get("agent_definition_hash")))) {
            throw new IllegalStateException("SUB_WORKFLOW_CHILD_RUN_IDENTITY_CONFLICT");
        }
        return output(node, completed, workflowId, childRunId, false);
    }

    private Map<String, Object> existingRun(
            OpsChatApplicationService chat,
            String runId,
            String projectId) {
        try {
            Map<String, Object> existing = chat.run(runId, projectId);
            return existing == null ? Map.of() : existing;
        } catch (IllegalArgumentException notFound) {
            String message = text(notFound.getMessage());
            if (message.startsWith("Work Session 不存在或不属于当前项目：")) {
                return Map.of();
            }
            throw notFound;
        }
    }

    private Map<String, Object> output(
            OpsWorkflowNode node,
            Map<String, Object> existing,
            String workflowId,
            String childRunId,
            boolean reused) {
        positiveInteger(existing.get("agent_version"), "SUB_WORKFLOW_DEFINITION_IDENTITY_REQUIRED");
        required(existing.get("agent_definition_hash"), "SUB_WORKFLOW_DEFINITION_IDENTITY_REQUIRED");
        Object rawResponse = existing.get("response");
        String content = "";
        if (rawResponse instanceof Map<?, ?> response) {
            content = text(response.get("content"));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", content);
        output.put("childRunId", childRunId);
        output.put("workflowId", workflowId);
        output.put("workflowVersion", existing.get("agent_version"));
        output.put("workflowDefinitionHash", text(existing.get("agent_definition_hash")));
        output.put("reused", reused);
        if (configObject(node, "structuredOutputKey") != null) {
            new DirectActionDataPolicy().validate(Map.of("structuredOutputKey", configObject(node, "structuredOutputKey")));
            var reference = new LinkedHashMap<>(output);
            reference.put("executionStatus", "SUCCEEDED");
            output.put("workflowData_" + config(node, "structuredOutputKey"), Map.copyOf(reference));
        }
        return Map.copyOf(output);
    }

    private String input(
            OpsWorkflowNode node,
            OpsAgentChatRequest parentRequest,
            Map<String, Object> state) {
        String key = config(node, "inputKey");
        if (!key.isBlank()) {
            Object value = state == null ? null : state.get(key);
            if (value == null) throw new IllegalArgumentException("SUB_WORKFLOW_INPUT_MISSING:" + key);
            if (value instanceof String string) return string;
            String json = CanonicalJson.stringifyPreservingOrder(value);
            new DirectActionDataPolicy().parseObject("{\"value\":" + json + "}");
            return json;
        }
        return text(parentRequest.getQuery());
    }

    private String config(OpsWorkflowNode node, String key) {
        return text(configObject(node, key));
    }

    private Object configObject(OpsWorkflowNode node, String key) {
        return node == null || node.getConfig() == null ? null : node.getConfig().get(key);
    }

    private Integer positiveInteger(Object value, String reason) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed > 0) return parsed;
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException(reason);
    }

    private int integer(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String childIdentity(String base, String suffix) {
        // Run/session journals use VARCHAR(80). Keep working legacy identities and
        // retain the hash suffix when truncating, including at deeper nesting levels.
        int prefixLimit = 80 - "-sub-".length() - suffix.length();
        return base.substring(0, Math.min(base.length(), prefixLimit)) + "-sub-" + suffix;
    }

    private String required(Object value, String reason) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
