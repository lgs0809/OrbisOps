package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectResourceApplicationServiceTest {

    @Test
    void createsUpdatesAndEnrichesProjectResourceThroughTypedBoundary() {
        Map<String, ProjectDefinition> projects = new LinkedHashMap<>();
        ProjectDefinitionApplicationService definitionService =
                new ProjectDefinitionApplicationService(definitionRepository(projects), ignored -> { });
        definitionService.create(Map.of("projectId", "project-1", "name", "Project One"));

        Map<String, ProjectResourceDefinition> resources = new LinkedHashMap<>();
        RecordingPreparationPort preparationPort = new RecordingPreparationPort();
        ProjectResourceApplicationService service = new ProjectResourceApplicationService(
                resourceRepository(resources), definitionService, preparationPort);

        Map<String, Object> created = service.create(Map.of(
                "projectId", "project-1",
                "resourceId", "Orders DB",
                "type", "pg",
                "environment", "PROD",
                "name", "Orders",
                "username", "reader",
                "passwordRef", "${env:ORDERS_DB_PASSWORD}"));

        assertEquals("orders-db", created.get("resourceId"));
        assertEquals("postgresql", created.get("type"));
        assertEquals("prod", created.get("environment"));
        assertEquals("postgresql://127.0.0.1:5432/app", created.get("endpoint"));
        assertEquals(false, preparationPort.lastRequest().partialUpdate());

        Map<String, Object> updated = service.update(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "environment", "TEST"));

        assertEquals("orders-db", updated.get("resourceId"));
        assertEquals("test", updated.get("environment"));
        assertEquals(true, preparationPort.lastRequest().partialUpdate());
        assertEquals("${env:ORDERS_DB_PASSWORD}",
                preparationPort.lastRequest().existingCredential().get("passwordRef"));

        Map<String, Object> permissionUpdated = service.updatePermission(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "permission", Map.of("maxRows", 20)));

        @SuppressWarnings("unchecked")
        Map<String, Object> permission =
                (Map<String, Object>) permissionUpdated.get("permission");
        assertEquals(20, permission.get("maxRows"));
        assertEquals(true, permission.get("enriched"));
        assertEquals(1, service.list("project-1").size());
        assertTrue(resources.containsKey("project-1::orders-db"));
    }

    private IProjectDefinitionRepository definitionRepository(
            Map<String, ProjectDefinition> projects) {
        return new IProjectDefinitionRepository() {
            @Override
            public List<ProjectDefinition> listEnabled() {
                return projects.values().stream().filter(ProjectDefinition::enabled).toList();
            }

            @Override
            public Optional<ProjectDefinition> find(String projectId) {
                return Optional.ofNullable(projects.get(projectId));
            }

            @Override
            public boolean exists(String projectId) {
                return projects.containsKey(projectId);
            }

            @Override
            public ProjectDefinition save(ProjectDefinition definition) {
                projects.put(definition.projectId(), definition);
                return definition;
            }
        };
    }

    private IProjectResourceRepository resourceRepository(
            Map<String, ProjectResourceDefinition> resources) {
        return new IProjectResourceRepository() {
            @Override
            public List<ProjectResourceDefinition> list(String projectId) {
                return resources.values().stream()
                        .filter(resource -> projectId.equals(resource.projectId()))
                        .toList();
            }

            @Override
            public Optional<ProjectResourceDefinition> find(
                    String projectId,
                    String resourceId) {
                return Optional.ofNullable(resources.get(projectId + "::" + resourceId));
            }

            @Override
            public ProjectResourceDefinition save(ProjectResourceDefinition resource) {
                resources.put(resource.projectId() + "::" + resource.resourceId(), resource);
                return resource;
            }
        };
    }

    private static final class RecordingPreparationPort
            implements ProjectResourcePreparationPort {

        private ProjectResourcePreparationRequest lastRequest;

        @Override
        public ProjectResourcePreparation prepare(ProjectResourcePreparationRequest request) {
            this.lastRequest = request;
            Map<String, Object> credential = new LinkedHashMap<>(request.existingCredential());
            credential.put("username", request.command().getOrDefault("username", "reader"));
            credential.put("passwordRef", request.command().getOrDefault(
                    "passwordRef",
                    credential.getOrDefault("passwordRef", "")));
            return new ProjectResourcePreparation(
                    request.type(),
                    credential,
                    Map.of("source", "preview", "objects", List.of()),
                    Map.of("readOnly", true),
                    "PREVIEW");
        }

        @Override
        public Map<String, Object> enrichPermission(
                String type,
                Map<String, Object> permission) {
            Map<String, Object> enriched = new LinkedHashMap<>(permission);
            enriched.put("enriched", true);
            return enriched;
        }

        private ProjectResourcePreparationRequest lastRequest() {
            return lastRequest;
        }
    }
}
