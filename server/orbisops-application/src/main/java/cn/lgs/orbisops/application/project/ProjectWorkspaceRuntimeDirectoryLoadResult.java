package cn.lgs.orbisops.application.project;

public record ProjectWorkspaceRuntimeDirectoryLoadResult(
        Status status,
        int projectCount,
        int resourceCount,
        int mcpCount
) {

    public ProjectWorkspaceRuntimeDirectoryLoadResult {
        status = status == null ? Status.FAILED : status;
        projectCount = Math.max(projectCount, 0);
        resourceCount = Math.max(resourceCount, 0);
        mcpCount = Math.max(mcpCount, 0);
    }

    public boolean loaded() {
        return status == Status.LOADED;
    }

    public enum Status {
        LOADED,
        EMPTY,
        FAILED
    }
}
