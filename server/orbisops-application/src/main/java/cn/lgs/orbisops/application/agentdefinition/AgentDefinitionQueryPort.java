package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Outbound query boundary for Agent Definitions and effective capability bindings. */
public interface AgentDefinitionQueryPort<D, B> {

    List<D> listAll();

    List<D> listForProject(String projectId);

    /** Immutable versions in descending version order, including drafts for administration. */
    List<D> listVersions(String agentId);

    D resolveDraft(String agentId);

    List<B> storedBindings(String agentId);

    List<B> deriveBindings(D definition);

    boolean projectScoped(D definition);

    String agentId(D definition);
}
