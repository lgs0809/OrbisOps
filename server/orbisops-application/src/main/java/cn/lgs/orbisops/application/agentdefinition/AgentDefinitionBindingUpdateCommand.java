package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Typed command after an outer adapter has parsed the compatibility binding request. */
public record AgentDefinitionBindingUpdateCommand<B>(
        String agentId,
        List<B> bindings) {

    public AgentDefinitionBindingUpdateCommand {
        String normalized = agentId == null ? "" : agentId.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("更新 Agent 绑定必须提供 agentId");
        }
        agentId = normalized;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
