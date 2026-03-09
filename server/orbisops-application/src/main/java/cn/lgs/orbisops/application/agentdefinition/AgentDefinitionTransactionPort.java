package cn.lgs.orbisops.application.agentdefinition;

import java.util.function.Supplier;

/** Transaction boundary for Agent Definition mutation use cases. */
public interface AgentDefinitionTransactionPort {

    <T> T required(Supplier<T> action);
}
