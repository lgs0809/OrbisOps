package cn.lgs.orbisops.application.agentdefinition;

/** Project lookup used while resolving a runnable project-owned Agent. */
public interface ProjectAgentDirectoryPort {

    boolean available();

    boolean exists(String projectId);

    String defaultAgentId(String projectId);
}
