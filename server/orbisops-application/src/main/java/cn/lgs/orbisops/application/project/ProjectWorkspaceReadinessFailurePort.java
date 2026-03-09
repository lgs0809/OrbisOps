package cn.lgs.orbisops.application.project;

public interface ProjectWorkspaceReadinessFailurePort {

    void readinessQueryFailed(String catalog, String projectId, RuntimeException error);
}
