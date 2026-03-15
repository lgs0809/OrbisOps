package cn.lgs.orbisops.domain.project.adapter.repository;

public interface IProjectWorkspaceReadinessRepository {

    boolean available();

    int countReadySourceRepositories(String projectId);

    int countEnabledExecutionResources(String projectId);
}
