package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.SubAgentToolBoundaryDecision;
import cn.lgs.orbisops.domain.agentdefinition.service.SubAgentToolBoundaryPolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Runtime adapter that applies the pure sub-agent tool-boundary domain decision. */
@Component
public final class OpsRuntimeSubAgentToolBoundaryPolicy {

    private final SubAgentToolBoundaryPolicy domainPolicy =
            new SubAgentToolBoundaryPolicy();
    private final OpsRuntimeToolAuthorityPolicy authorityPolicy =
            new OpsRuntimeToolAuthorityPolicy();

    public void enforce(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        authorityPolicy.enforce(context);
        OpsAgentScopeConfig scope = context.getAgentScope();
        if (scope == null || !StringUtils.hasText(scope.getRole())) return;

        List<ToolCallback> availableTools = context.getTools() == null
                ? List.of()
                : context.getTools();
        SubAgentToolBoundaryDecision decision = domainPolicy.evaluate(
                scope.getRole(),
                scope.getMaxDepth(),
                scope.getAllowedToolNames(),
                availableTools.stream().map(this::toolName).toList());
        if (!decision.active()) return;

        Set<String> permittedNames = new LinkedHashSet<>(decision.permittedToolNames());
        List<ToolCallback> permittedTools = availableTools.stream()
                .filter(tool -> permittedNames.contains(toolName(tool)))
                .toList();
        context.setTools(new ArrayList<>(permittedTools));
        context.getMetadata().put("subAgentRole", decision.role());
        context.getMetadata().put("subAgentMaxDepth", decision.maxDepth());
        context.getMetadata().put(
                "subAgentAllowedTools",
                decision.allowedToolPatterns());
        context.getMetadata().put("subAgentToolCount", permittedTools.size());
    }

    private String toolName(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) return "";
        return callback.getToolDefinition().name();
    }
}
