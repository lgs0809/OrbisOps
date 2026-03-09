package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.RuntimeWorkflowBindingApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.RuntimeWorkflowBindingInput;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowEdge;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowNode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowResourceReference;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** ACL from structural Agent Definition and executable runtime objects to a persistable bound plan. */
@Component
public final class OpsRuntimeWorkflowBindingAdapter {

    private static final String BINDING_SOURCE = "RUNTIME_RESOURCE_PIPELINE";

    private final RuntimeWorkflowBindingApplicationService bindingService;
    private final BoundWorkflowPlanPolicy fingerprintPolicy;

    public OpsRuntimeWorkflowBindingAdapter() {
        this(new RuntimeWorkflowBindingApplicationService(), new BoundWorkflowPlanPolicy());
    }

    OpsRuntimeWorkflowBindingAdapter(
            RuntimeWorkflowBindingApplicationService bindingService,
            BoundWorkflowPlanPolicy fingerprintPolicy) {
        if (bindingService == null) throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_SERVICE_REQUIRED");
        if (fingerprintPolicy == null) throw new IllegalArgumentException("BOUND_WORKFLOW_FINGERPRINT_POLICY_REQUIRED");
        this.bindingService = bindingService;
        this.fingerprintPolicy = fingerprintPolicy;
    }

    public BoundWorkflowExecutionPlan bind(
            CompiledAgentDefinitionVersion compiled,
            OpsRuntimeResourceBundle runtime,
            RuntimeContextBundleSnapshot contextBundle,
            String sessionId,
            String runId,
            Instant boundAt) {
        return bind(compiled, runtime, Map.of(), contextBundle, sessionId, runId, boundAt);
    }

    public BoundWorkflowExecutionPlan bind(
            CompiledAgentDefinitionVersion compiled,
            OpsRuntimeResourceBundle runtime,
            Map<String, OpsRuntimeResourceBundle> nodeRuntimes,
            RuntimeContextBundleSnapshot contextBundle,
            String sessionId,
            String runId,
            Instant boundAt) {
        if (compiled == null) throw new IllegalArgumentException("COMPILED_WORKFLOW_REQUIRED");
        if (!compiled.versioned()) throw new IllegalArgumentException("COMPILED_WORKFLOW_VERSION_REQUIRED");
        if (runtime == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_BUNDLE_REQUIRED");
        if (contextBundle == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_REQUIRED");
        if (!compiled.agentId().equals(contextBundle.agentId())) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_AGENT_CONTEXT_MISMATCH");
        }
        String effectiveSessionId = required(sessionId, "RUNTIME_WORKFLOW_SESSION_ID_REQUIRED");
        String effectiveRunId = required(runId, "RUNTIME_WORKFLOW_RUN_ID_REQUIRED");
        if (StringUtils.hasText(contextBundle.sessionId())
                && !effectiveSessionId.equals(contextBundle.sessionId())) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_SESSION_CONTEXT_MISMATCH");
        }
        if (StringUtils.hasText(contextBundle.runId())
                && !effectiveRunId.equals(contextBundle.runId())) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_RUN_CONTEXT_MISMATCH");
        }

        RuntimeWorkflowBindingInput input = new RuntimeWorkflowBindingInput(
                compiled.schemaVersion(),
                compiled.definitionVersion(),
                compiled.definitionHash(),
                compiled.agentId(),
                effectiveSessionId,
                effectiveRunId,
                firstText(runtime.getProjectId(), contextBundle.projectId()),
                contextBundle.bundleId(),
                contextBundle.bundleHash(),
                compiled.startNodeId(),
                compiled.nodes().stream()
                        .map(node -> nodeInput(node, runtimeForNode(node.nodeId(), runtime, nodeRuntimes), contextBundle))
                        .toList(),
                routeInputs(compiled.edges()),
                sharedResources(compiled, contextBundle),
                boundAt == null ? Instant.now() : boundAt);
        return bindingService.bind(input);
    }

    private RuntimeWorkflowBindingInput.NodeInput nodeInput(
            CompiledWorkflowNode node,
            OpsRuntimeResourceBundle runtime,
            RuntimeContextBundleSnapshot contextBundle) {
        return new RuntimeWorkflowBindingInput.NodeInput(
                node.nodeId(),
                node.nodeType().name(),
                node.publishedType(),
                node.compilerId(),
                fingerprintPolicy.fingerprint(node.config()),
                node.resources().stream()
                        .map(reference -> bindResource(reference, runtime, contextBundle))
                        .toList());
    }

    private OpsRuntimeResourceBundle runtimeForNode(
            String nodeId,
            OpsRuntimeResourceBundle fallback,
            Map<String, OpsRuntimeResourceBundle> nodeRuntimes) {
        if (nodeRuntimes == null || nodeRuntimes.isEmpty() || !StringUtils.hasText(nodeId)) {
            return fallback;
        }
        OpsRuntimeResourceBundle nodeRuntime = nodeRuntimes.get(nodeId);
        return nodeRuntime == null ? fallback : nodeRuntime;
    }

    private List<RuntimeWorkflowBindingInput.RouteInput> routeInputs(
            List<CompiledWorkflowEdge> edges) {
        List<CompiledWorkflowEdge> safe = edges == null ? List.of() : edges;
        return IntStream.range(0, safe.size())
                .mapToObj(index -> routeInput(safe.get(index), index))
                .toList();
    }

    private RuntimeWorkflowBindingInput.RouteInput routeInput(
            CompiledWorkflowEdge edge,
            int index) {
        String edgeId = StringUtils.hasText(edge.edgeId())
                ? edge.edgeId().trim()
                : edge.sourceNodeId() + "->" + edge.targetNodeId() + "#" + index;
        Map<String, Object> ruleFacts = new LinkedHashMap<>();
        ruleFacts.put("mode", edge.rule().mode().name());
        ruleFacts.put("expression", edge.rule().expression());
        ruleFacts.put("ast", edge.compiledRule().toString());
        return new RuntimeWorkflowBindingInput.RouteInput(
                edgeId,
                edge.sourceNodeId(),
                edge.targetNodeId(),
                edge.routeMode().name(),
                fingerprintPolicy.fingerprint(ruleFacts),
                fingerprintPolicy.fingerprint(edge.dataMapping()),
                edge.priority(),
                edge.defaultRoute(),
                edge.feedbackEdge());
    }

    private List<BoundWorkflowResourceSnapshot> sharedResources(
            CompiledAgentDefinitionVersion compiled,
            RuntimeContextBundleSnapshot contextBundle) {
        String policyHash = StringUtils.hasText(contextBundle.runtimeBoundaryHash())
                ? contextBundle.runtimeBoundaryHash()
                : fingerprintPolicy.fingerprint(contextBundle.payload().getOrDefault("policyRefs", List.of()));
        return List.of(
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.MEMORY_CONTEXT,
                        contextBundle.bundleId(),
                        0,
                        contextBundle.bundleHash(),
                        "RUNTIME_CONTEXT_BUNDLE",
                        true,
                        true),
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.RUNTIME_POLICY,
                        "runtime-policy:" + compiled.agentId(),
                        compiled.definitionVersion(),
                        policyHash,
                        "RUNTIME_CONTEXT_BUNDLE",
                        true,
                        true));
    }

    private BoundWorkflowResourceSnapshot bindResource(
            AgentWorkflowResourceReference reference,
            OpsRuntimeResourceBundle runtime,
            RuntimeContextBundleSnapshot contextBundle) {
        BoundWorkflowResourceKind kind = switch (reference.resourceType()) {
            case MODEL -> BoundWorkflowResourceKind.MODEL;
            case KNOWLEDGE_BASE -> BoundWorkflowResourceKind.KNOWLEDGE_BASE;
            case SKILL -> BoundWorkflowResourceKind.SKILL;
            case MCP, INLINE_MCP -> BoundWorkflowResourceKind.MCP;
            case EXECUTION_TARGET -> BoundWorkflowResourceKind.TOOL;
        };
        assertResolved(reference, runtime, contextBundle);
        ResourceVersionHash versionHash = versionHash(reference, kind, runtime, contextBundle);
        return new BoundWorkflowResourceSnapshot(
                kind,
                reference.resourceId(),
                versionHash.version(),
                versionHash.hash(),
                BINDING_SOURCE,
                true,
                switch (kind) {
                    case MODEL, SKILL, KNOWLEDGE_BASE, MEMORY_CONTEXT, RUNTIME_POLICY -> true;
                    case TOOL, MCP -> false;
                });
    }

    private void assertResolved(
            AgentWorkflowResourceReference reference,
            OpsRuntimeResourceBundle runtime,
            RuntimeContextBundleSnapshot contextBundle) {
        String id = reference.resourceId();
        boolean resolved = switch (reference.resourceType()) {
            case MODEL -> id.equals(runtime.getModelId());
            case KNOWLEDGE_BASE -> id.equals(runtime.getKnowledgeBaseId());
            case SKILL -> safe(runtime.getSkillNames()).contains(id)
                    || skillRefs(contextBundle).stream().anyMatch(ref -> id.equals(text(ref.get("skillId"))));
            case MCP, INLINE_MCP -> safe(runtime.getMcpIds()).contains(id)
                    || safe(runtime.getMcpServers()).stream().anyMatch(server -> id.equals(server.getMcpId())
                    || id.equals(server.getName()));
            case EXECUTION_TARGET -> toolNames(runtime).contains(id)
                    || toolsetRefs(contextBundle).stream().anyMatch(ref -> id.equals(text(ref.get("toolsetId")))
                    || id.equals(text(ref.get("toolId"))));
        };
        if (!resolved) {
            throw new IllegalStateException("RUNTIME_WORKFLOW_RESOURCE_UNRESOLVED:"
                    + reference.resourceType() + ":" + id);
        }
    }

    private ResourceVersionHash versionHash(
            AgentWorkflowResourceReference reference,
            BoundWorkflowResourceKind kind,
            OpsRuntimeResourceBundle runtime,
            RuntimeContextBundleSnapshot contextBundle) {
        if (kind == BoundWorkflowResourceKind.SKILL) {
            Map<String, Object> ref = skillRefs(contextBundle).stream()
                    .filter(item -> reference.resourceId().equals(text(item.get("skillId"))))
                    .findFirst()
                    .orElse(Map.of());
            long version = longValue(ref.get("version"));
            String hash = firstText(text(ref.get("skillHash")), text(ref.get("hash")));
            if (!StringUtils.hasText(hash)) {
                hash = fingerprintPolicy.fingerprint(Map.of(
                        "kind", kind.name(), "id", reference.resourceId(), "version", version,
                        "skillBoundary", contextBundle.usedSkillRefsHash()));
            }
            return new ResourceVersionHash(version, hash);
        }
        String boundaryHash = switch (kind) {
            case TOOL -> contextBundle.toolsetBoundaryHash();
            case MCP -> contextBundle.runtimeBoundaryHash();
            case MODEL -> fingerprintPolicy.fingerprint(Map.of(
                    "modelId", text(runtime.getModelId()),
                    "modelVersion", text(runtime.getMetadata().get("modelVersion"))));
            case KNOWLEDGE_BASE -> fingerprintPolicy.fingerprint(Map.of(
                    "knowledgeBaseId", text(runtime.getKnowledgeBaseId()),
                    "contextBundleHash", contextBundle.bundleHash()));
            default -> contextBundle.runtimeBoundaryHash();
        };
        String hash = StringUtils.hasText(boundaryHash)
                ? boundaryHash
                : fingerprintPolicy.fingerprint(Map.of("kind", kind.name(), "id", reference.resourceId()));
        return new ResourceVersionHash(0, hash);
    }

    private Set<String> toolNames(OpsRuntimeResourceBundle runtime) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (ToolCallback callback : safe(runtime.getTools())) {
            if (callback != null && callback.getToolDefinition() != null
                    && StringUtils.hasText(callback.getToolDefinition().name())) {
                names.add(callback.getToolDefinition().name().trim());
            }
        }
        return Set.copyOf(names);
    }

    private List<Map<String, Object>> skillRefs(RuntimeContextBundleSnapshot contextBundle) {
        return contextBundle.usedSkillVersionRefs();
    }

    private List<Map<String, Object>> toolsetRefs(RuntimeContextBundleSnapshot contextBundle) {
        Object value = contextBundle.payload().get("toolsetRefs");
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
            result.add(Map.copyOf(copy));
        }
        return List.copyOf(result);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        try {
            return Math.max(0L, Long.parseLong(text(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ResourceVersionHash(long version, String hash) {
    }
}
