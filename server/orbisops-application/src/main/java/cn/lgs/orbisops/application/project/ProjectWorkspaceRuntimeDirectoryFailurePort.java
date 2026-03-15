package cn.lgs.orbisops.application.project;

public interface ProjectWorkspaceRuntimeDirectoryFailurePort {

    void loadFailed(RuntimeException error);
}
