package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for the project default Agent bootstrap process. */
public interface ProjectDefaultAgentAuditPort<D> {

    void record(
            String action,
            String agentId,
            D previousDefinition,
            D publishedDefinition,
            String suiteId,
            String evalRunId);
}
