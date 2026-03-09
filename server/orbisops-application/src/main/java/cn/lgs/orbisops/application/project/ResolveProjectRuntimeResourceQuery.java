package cn.lgs.orbisops.application.project;

import java.util.Optional;

public interface ResolveProjectRuntimeResourceQuery {

    Optional<ProjectWorkspaceRuntimeResource> resolve(String projectId, String resourceId);
}
