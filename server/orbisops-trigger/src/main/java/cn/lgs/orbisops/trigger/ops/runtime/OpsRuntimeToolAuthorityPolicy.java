package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime capability projection. Ordinary Agents expose only executable or proposal-only
 * capabilities; PROD execution remains exclusive to an approved LANDING runtime.
 */
public final class OpsRuntimeToolAuthorityPolicy {

    enum Exposure {
        EXECUTABLE,
        PROPOSABLE_ONLY,
        HIDDEN
    }

    public void enforce(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        AgentRunExecutionContext runAuthority = context.getExecutionContext();
        assertFresh(runAuthority);
        OpsRuntimeAgentAuthority agentAuthority = OpsRuntimeAgentAuthority.resolve(context);
        AgentExecutionStage effectiveStage = effectiveStage(agentAuthority);
        List<ToolCallback> exposed = new ArrayList<>();
        int executableCount = 0;
        int proposableCount = 0;
        for (ToolCallback callback : safe(context.getTools())) {
            OpsRuntimeToolAuthorityDescriptor descriptor = authority(callback);
            Exposure exposure = exposure(runAuthority, agentAuthority, effectiveStage, descriptor);
            if (exposure == Exposure.EXECUTABLE) {
                exposed.add(guard(runAuthority, agentAuthority, effectiveStage, callback, descriptor));
                executableCount++;
                continue;
            }
            if (exposure == Exposure.PROPOSABLE_ONLY) {
                exposed.add(proposal(context, callback, descriptor));
                proposableCount++;
                continue;
            }
            context.record(OpsRuntimeEvent.builder()
                    .eventType("RUNTIME_TOOL_AUTHORITY_BLOCKED")
                    .status("BLOCKED")
                    .summary("工具超出当前 Agent Authority：" + toolName(callback))
                    .payload(Map.of(
                            "tool", toolName(callback),
                            "runStage", runAuthority == null
                                    ? AgentExecutionStage.INVESTIGATE.name()
                                    : runAuthority.stage().name(),
                            "effectiveStage", effectiveStage.name(),
                            "agentAuthority", agentAuthority.name(),
                            "exposure", Exposure.HIDDEN.name(),
                            "capabilityProfile", runAuthority == null
                                    ? "UNTRUSTED_READ_ONLY"
                                    : runAuthority.capabilityProfile().name(),
                            "authorityDescriptorPresent", descriptor != null,
                            "authoritySource", descriptor == null ? "" : descriptor.source()))
                    .build());
        }
        context.setTools(exposed);
        context.getMetadata().put("authorityFilteredToolCount", exposed.size());
        context.getMetadata().put("authorityExecutableToolCount", executableCount);
        context.getMetadata().put("authorityProposableToolCount", proposableCount);
        context.getMetadata().put("agentAuthority", agentAuthority.name());
        context.getMetadata().put("authorityContextPresent", runAuthority != null);
        context.getMetadata().put("typedToolAuthority", true);
    }

    private ToolCallback guard(
            AgentRunExecutionContext runAuthority,
            OpsRuntimeAgentAuthority agentAuthority,
            AgentExecutionStage effectiveStage,
            ToolCallback delegate,
            OpsRuntimeToolAuthorityDescriptor descriptor) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            @Override
            public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override
            public String call(String toolInput) {
                assertInvocation(runAuthority, agentAuthority, effectiveStage, delegate, descriptor);
                return delegate.call(toolInput);
            }
            @Override
            public String call(String toolInput, ToolContext toolContext) {
                assertInvocation(runAuthority, agentAuthority, effectiveStage, delegate, descriptor);
                return delegate.call(toolInput, toolContext);
            }
        };
    }

    private ToolCallback proposal(
            OpsRuntimeResourceContext context,
            ToolCallback delegate,
            OpsRuntimeToolAuthorityDescriptor descriptor) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            @Override
            public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override
            public String call(String toolInput) {
                return proposedAction(context, delegate, descriptor, toolInput);
            }
            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return proposedAction(context, delegate, descriptor, toolInput);
            }
        };
    }

    private String proposedAction(
            OpsRuntimeResourceContext context,
            ToolCallback callback,
            OpsRuntimeToolAuthorityDescriptor descriptor,
            String toolInput) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("status", "REQUIRES_CHANGE_PACKAGE");
        action.put("exposure", Exposure.PROPOSABLE_ONLY.name());
        action.put("toolName", toolName(callback));
        action.put("arguments", parseInput(toolInput));
        action.put("source", descriptor == null ? "" : descriptor.source());
        action.put("effect", descriptor == null ? "" : effect(descriptor.semantics()));
        action.put("riskLevel", descriptor == null ? "" : descriptor.semantics().riskLevel().name());
        action.put("runId", runtimeRunId(context));
        action.put("nodeId", context.getNode() == null ? "" : value(context.getNode().getNodeId()));
        action.put("observedAt", Instant.now().toString());
        action.put("remoteCallExecuted", false);
        Map<String, Object> envelope = Map.of(
                "status", "REQUIRES_CHANGE_PACKAGE",
                "allowed", false,
                "remoteCallExecuted", false,
                "proposedAction", Map.copyOf(action));
        context.record(OpsRuntimeEvent.builder()
                .eventType("PROPOSED_ACTION_RECORDED")
                .status("SUCCEEDED")
                .nodeId(context.getNode() == null ? null : context.getNode().getNodeId())
                .summary("已记录生产变更意图，未执行真实工具：" + toolName(callback))
                .payload(envelope)
                .build());
        return JSON.toJSONString(envelope);
    }

    private void assertInvocation(
            AgentRunExecutionContext runAuthority,
            OpsRuntimeAgentAuthority agentAuthority,
            AgentExecutionStage effectiveStage,
            ToolCallback callback,
            OpsRuntimeToolAuthorityDescriptor descriptor) {
        assertFresh(runAuthority);
        if (exposure(runAuthority, agentAuthority, effectiveStage, descriptor) != Exposure.EXECUTABLE) {
            throw new SecurityException("RUNTIME_TOOL_AUTHORITY_REVOKED:" + toolName(callback));
        }
        if (effectiveStage == AgentExecutionStage.LANDING) {
            var approved = runAuthority == null
                    ? null
                    : runAuthority.approvedPackage().orElse(null);
            if (approved == null) throw new SecurityException("LANDING_APPROVED_PACKAGE_REQUIRED");
            if (approved.expired(Instant.now())) throw new SecurityException("LANDING_APPROVAL_EXPIRED");
        }
    }

    private Exposure exposure(
            AgentRunExecutionContext runAuthority,
            OpsRuntimeAgentAuthority agentAuthority,
            AgentExecutionStage effectiveStage,
            OpsRuntimeToolAuthorityDescriptor descriptor) {
        if (descriptor == null) return Exposure.HIDDEN;
        ToolSemantics semantics = descriptor.semantics();
        if (agentAuthority == OpsRuntimeAgentAuthority.OBSERVE_ONLY) {
            boolean executable = descriptor.allowsStage(AgentExecutionStage.INVESTIGATE)
                    && (semantics.readOnly() || descriptor.delegatedInvocationPolicy());
            return executable ? Exposure.EXECUTABLE : Exposure.HIDDEN;
        }
        if (agentAuthority == OpsRuntimeAgentAuthority.PREPARE_CHANGE) {
            if (semantics.writesTargetResource()
                    && semantics.requiresChangePackage()
                    && !descriptor.allowsStage(AgentExecutionStage.PREPARE)) {
                return Exposure.PROPOSABLE_ONLY;
            }
            return descriptor.allowsStage(AgentExecutionStage.PREPARE)
                    ? Exposure.EXECUTABLE
                    : Exposure.HIDDEN;
        }
        if (runAuthority == null
                || runAuthority.stage() != AgentExecutionStage.LANDING
                || runAuthority.approvedPackage().isEmpty()) {
            return Exposure.HIDDEN;
        }
        return descriptor.allowsStage(AgentExecutionStage.LANDING)
                ? Exposure.EXECUTABLE
                : Exposure.HIDDEN;
    }

    private AgentExecutionStage effectiveStage(OpsRuntimeAgentAuthority authority) {
        return switch (authority) {
            case OBSERVE_ONLY -> AgentExecutionStage.INVESTIGATE;
            case PREPARE_CHANGE -> AgentExecutionStage.PREPARE;
            case PROD_FULL -> AgentExecutionStage.LANDING;
        };
    }

    private String effect(ToolSemantics semantics) {
        if (semantics == null) return "UNKNOWN";
        if (semantics.readOnly()) return "READ_ONLY";
        if (semantics.writesRepairWorkspace()) return "REPAIR_WORKSPACE_WRITE";
        if (semantics.writesTargetResource()) return "TARGET_RESOURCE_WRITE";
        return "COMMAND";
    }

    private Object parseInput(String input) {
        if (input == null || input.isBlank()) return Map.of();
        try {
            Object parsed = JSON.parse(input);
            return parsed == null ? Map.of() : parsed;
        } catch (RuntimeException ignored) {
            return input;
        }
    }

    private String runtimeRunId(OpsRuntimeResourceContext context) {
        if (context == null || context.getRequest() == null) return "";
        return value(context.getRequest().getRunId());
    }

    private void assertFresh(AgentRunExecutionContext authority) {
        if (authority != null && authority.expired(Instant.now())) {
            throw new SecurityException("AGENT_RUN_EXECUTION_CONTEXT_EXPIRED");
        }
    }

    private OpsRuntimeToolAuthorityDescriptor authority(ToolCallback callback) {
        return OpsRuntimeGovernedToolCallback.authorityOf(callback);
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null || definition.name() == null ? "" : definition.name();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<ToolCallback> safe(List<ToolCallback> callbacks) {
        return callbacks == null ? List.of() : callbacks;
    }
}
