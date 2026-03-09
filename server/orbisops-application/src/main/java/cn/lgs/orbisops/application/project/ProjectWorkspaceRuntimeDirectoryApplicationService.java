package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProjectWorkspaceRuntimeDirectoryApplicationService
        implements ResolveProjectRuntimeResourceQuery {

    private final ProjectDefinitionSnapshotPort definitionSnapshotPort;
    private final ProjectResourceSnapshotPort resourceSnapshotPort;
    private final ProjectMcpSnapshotPort mcpSnapshotPort;
    private final ProjectResourceCredentialResolutionPort credentialResolutionPort;
    private final ProjectWorkspaceRuntimeDirectoryFailurePort failurePort;

    private final Map<String, ProjectDefinition> projects = new LinkedHashMap<>();
    private final Map<String, List<ProjectResourceDefinition>> resources = new LinkedHashMap<>();
    private final Map<String, List<ProjectMcpDefinition>> mcps = new LinkedHashMap<>();

    public ProjectWorkspaceRuntimeDirectoryApplicationService(
            ProjectDefinitionSnapshotPort definitionSnapshotPort,
            ProjectResourceSnapshotPort resourceSnapshotPort,
            ProjectMcpSnapshotPort mcpSnapshotPort,
            ProjectResourceCredentialResolutionPort credentialResolutionPort,
            ProjectWorkspaceRuntimeDirectoryFailurePort failurePort) {
        if (definitionSnapshotPort == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SNAPSHOT_PORT_REQUIRED");
        }
        if (resourceSnapshotPort == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_SNAPSHOT_PORT_REQUIRED");
        }
        if (mcpSnapshotPort == null) {
            throw new IllegalArgumentException("PROJECT_MCP_SNAPSHOT_PORT_REQUIRED");
        }
        if (credentialResolutionPort == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_CREDENTIAL_RESOLUTION_PORT_REQUIRED");
        }
        if (failurePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_RUNTIME_DIRECTORY_FAILURE_PORT_REQUIRED");
        }
        this.definitionSnapshotPort = definitionSnapshotPort;
        this.resourceSnapshotPort = resourceSnapshotPort;
        this.mcpSnapshotPort = mcpSnapshotPort;
        this.credentialResolutionPort = credentialResolutionPort;
        this.failurePort = failurePort;
    }

    public synchronized ProjectWorkspaceRuntimeDirectoryLoadResult loadPersisted() {
        try {
            List<ProjectDefinition> loadedProjects = definitions(definitionSnapshotPort.listEnabled());
            if (loadedProjects.isEmpty()) {
                clearInternal();
                return result(ProjectWorkspaceRuntimeDirectoryLoadResult.Status.EMPTY, 0, 0, 0);
            }

            Map<String, ProjectDefinition> nextProjects = new LinkedHashMap<>();
            Map<String, List<ProjectResourceDefinition>> nextResources = new LinkedHashMap<>();
            Map<String, List<ProjectMcpDefinition>> nextMcps = new LinkedHashMap<>();
            for (ProjectDefinition definition : loadedProjects) {
                nextProjects.put(definition.projectId(), definition);
                nextResources.put(definition.projectId(), new ArrayList<>());
                nextMcps.put(definition.projectId(), new ArrayList<>());
            }

            int resourceCount = 0;
            for (ProjectResourceDefinition resource : resourceDefinitions(resourceSnapshotPort.listAll())) {
                List<ProjectResourceDefinition> projectResources = nextResources.get(resource.projectId());
                if (projectResources == null) {
                    continue;
                }
                upsertResource(projectResources, resource);
                resourceCount++;
            }

            int mcpCount = 0;
            for (ProjectMcpDefinition mcp : mcpDefinitions(mcpSnapshotPort.listAll())) {
                List<ProjectMcpDefinition> projectMcps = nextMcps.get(mcp.projectId());
                if (projectMcps == null) {
                    continue;
                }
                appendLoadedMcp(projectMcps, mcp);
                mcpCount++;
            }

            projects.clear();
            projects.putAll(nextProjects);
            resources.clear();
            nextResources.forEach((projectId, values) -> resources.put(projectId, new ArrayList<>(values)));
            mcps.clear();
            nextMcps.forEach((projectId, values) -> mcps.put(projectId, new ArrayList<>(values)));
            return result(ProjectWorkspaceRuntimeDirectoryLoadResult.Status.LOADED,
                    projects.size(), resourceCount, mcpCount);
        } catch (RuntimeException error) {
            clearInternal();
            failurePort.loadFailed(error);
            return result(ProjectWorkspaceRuntimeDirectoryLoadResult.Status.FAILED, 0, 0, 0);
        }
    }

    public synchronized void clear() {
        clearInternal();
    }

    public synchronized void materializeDefinition(ProjectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_REQUIRED");
        }
        projects.put(definition.projectId(), definition);
        resources.computeIfAbsent(definition.projectId(), ignored -> new ArrayList<>());
        mcps.computeIfAbsent(definition.projectId(), ignored -> new ArrayList<>());
    }

    public synchronized boolean materializeResource(ProjectResourceDefinition resource) {
        if (resource == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        }
        List<ProjectResourceDefinition> projectResources = resources.get(resource.projectId());
        if (!projects.containsKey(resource.projectId()) || projectResources == null) {
            return false;
        }
        upsertResource(projectResources, resource);
        return true;
    }

    public synchronized boolean materializeMcp(ProjectMcpDefinition mcp) {
        if (mcp == null) {
            throw new IllegalArgumentException("PROJECT_MCP_REQUIRED");
        }
        List<ProjectMcpDefinition> projectMcps = mcps.get(mcp.projectId());
        if (!projects.containsKey(mcp.projectId()) || projectMcps == null) {
            return false;
        }
        projectMcps.removeIf(current -> mcp.mcpId().equals(current.mcpId()));
        projectMcps.add(0, mcp);
        return true;
    }

    public synchronized List<ProjectDefinition> projects() {
        return List.copyOf(projects.values());
    }

    public synchronized Optional<ProjectDefinition> project(String projectId) {
        String id = value(projectId);
        return id.isBlank() ? Optional.empty() : Optional.ofNullable(projects.get(id));
    }

    public synchronized boolean projectExists(String projectId) {
        return project(projectId).isPresent();
    }

    public synchronized List<ProjectResourceDefinition> resources(String projectId) {
        String id = value(projectId);
        List<ProjectResourceDefinition> values = id.isBlank() ? null : resources.get(id);
        return values == null ? List.of() : List.copyOf(values);
    }

    public synchronized Optional<ProjectResourceDefinition> resource(String projectId, String resourceId) {
        String project = value(projectId);
        String resource = value(resourceId);
        if (project.isBlank() || resource.isBlank()) {
            return Optional.empty();
        }
        return resources(project).stream()
                .filter(candidate -> resource.equals(candidate.resourceId()))
                .findFirst();
    }

    public synchronized List<ProjectMcpDefinition> mcps(String projectId) {
        String id = value(projectId);
        List<ProjectMcpDefinition> values = id.isBlank() ? null : mcps.get(id);
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public synchronized Optional<ProjectWorkspaceRuntimeResource> resolve(
            String projectId,
            String resourceId) {
        return resource(projectId, resourceId).map(resource -> new ProjectWorkspaceRuntimeResource(
                resource.resourceId(),
                resource.projectId(),
                resource.type().value(),
                resource.environment(),
                resource.endpoint(),
                credentialResolutionPort.resolve(resource.credential()),
                resource.permission(),
                resource.status()));
    }

    private void upsertResource(
            List<ProjectResourceDefinition> projectResources,
            ProjectResourceDefinition resource) {
        for (int index = 0; index < projectResources.size(); index++) {
            if (resource.resourceId().equals(projectResources.get(index).resourceId())) {
                projectResources.set(index, resource);
                return;
            }
        }
        projectResources.add(resource);
    }

    private void appendLoadedMcp(
            List<ProjectMcpDefinition> projectMcps,
            ProjectMcpDefinition mcp) {
        for (int index = 0; index < projectMcps.size(); index++) {
            if (mcp.mcpId().equals(projectMcps.get(index).mcpId())) {
                projectMcps.set(index, mcp);
                return;
            }
        }
        projectMcps.add(mcp);
    }

    private List<ProjectDefinition> definitions(List<ProjectDefinition> source) {
        return source == null ? List.of() : source.stream()
                .filter(value -> value != null)
                .toList();
    }

    private List<ProjectResourceDefinition> resourceDefinitions(List<ProjectResourceDefinition> source) {
        return source == null ? List.of() : source.stream()
                .filter(value -> value != null)
                .toList();
    }

    private List<ProjectMcpDefinition> mcpDefinitions(List<ProjectMcpDefinition> source) {
        return source == null ? List.of() : source.stream()
                .filter(value -> value != null)
                .toList();
    }

    private ProjectWorkspaceRuntimeDirectoryLoadResult result(
            ProjectWorkspaceRuntimeDirectoryLoadResult.Status status,
            int projectCount,
            int resourceCount,
            int mcpCount) {
        return new ProjectWorkspaceRuntimeDirectoryLoadResult(
                status, projectCount, resourceCount, mcpCount);
    }

    private void clearInternal() {
        projects.clear();
        resources.clear();
        mcps.clear();
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
