package cn.lgs.orbisops.application.agentdefinition;

/** Factory boundary for the platform-safe fallback Agent Definition. */
public interface AgentDefinitionFallbackFactory<D> {

    D create();
}
