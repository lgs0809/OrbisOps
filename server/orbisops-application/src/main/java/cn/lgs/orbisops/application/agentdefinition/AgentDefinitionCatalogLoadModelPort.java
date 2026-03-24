package cn.lgs.orbisops.application.agentdefinition;

/** Adapter boundary for source precedence facts used during catalog loading. */
public interface AgentDefinitionCatalogLoadModelPort<D> {

    String agentId(D definition);

    Integer version(D definition);

    String projectId(D definition);

    String source(D definition);
}
