package cn.lgs.orbisops.application.agentdefinition;

/** Outbound boundary for project admission and Agent evaluation execution. */
public interface AgentDefinitionEvalPort<R, O> {

    String requireExistingProject(String projectId);

    O createSuite(
            String projectId,
            String agentId,
            R request,
            String actor);

    O run(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor);
}
