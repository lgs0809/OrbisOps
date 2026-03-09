package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Outbound mutable-definition boundary required by capability binding updates. */
public interface AgentDefinitionBindingUpdatePort<D, B, V> {

    D resolveDraft(String agentId);

    D applyBindings(D definition, List<B> bindings);

    void assertProjectAndBindingsValid(D definition);

    D normalizeExecutionShape(D definition);

    V effectiveBindings(D savedDefinition);
}
