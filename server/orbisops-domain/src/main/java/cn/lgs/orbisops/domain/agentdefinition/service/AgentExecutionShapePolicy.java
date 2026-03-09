package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionNodeFact;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionShapeDecision;

import java.util.List;
import java.util.Locale;

/** Determines the canonical graph engine and retires legacy top-level AgentScope execution fields. */
public final class AgentExecutionShapePolicy {

    public AgentExecutionShapeDecision decide(List<AgentExecutionNodeFact> nodes) {
        boolean hasReactNode = (nodes == null
                ? List.<AgentExecutionNodeFact>of()
                : nodes).stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(this::isReactNode);
        return new AgentExecutionShapeDecision(
                hasReactNode ? "HYBRID" : "GRAPH",
                true);
    }

    private boolean isReactNode(AgentExecutionNodeFact node) {
        String type = normalize(node.type());
        String mode = normalize(node.mode());
        String subEngine = normalize(node.subEngine());
        return "AGENTSCOPE".equals(type)
                || "AGENTSCOPE".equals(subEngine)
                || "REACT".equals(mode);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
