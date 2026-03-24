package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDefinitionQueryServiceTest {

    @Test
    void shouldResolveCurrentAndReturnDetachedSnapshot() {
        Fixture fixture = new Fixture();
        Definition stored = fixture.add("agent-a", 1, "project-a", AgentDefinitionLifecycle.PUBLISHED, true);

        Definition resolved = fixture.service.resolve("agent-a", null, false);

        assertEquals("agent-a", resolved.agentId);
        assertNotSame(stored, resolved);
    }

    @Test
    void shouldRequirePreviewForDraftAndValidatedVersions() {
        Fixture fixture = new Fixture();
        fixture.add("agent-a", 1, "project-a", AgentDefinitionLifecycle.PUBLISHED, true);
        fixture.add("agent-a", 2, "project-a", AgentDefinitionLifecycle.DRAFT, false);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.resolve("agent-a", 2, false));
        assertEquals(2, fixture.service.resolve("agent-a", 2, true).version);
    }

    @Test
    void shouldResolveProjectDefaultAndRejectForeignAgent() {
        Fixture fixture = new Fixture();
        fixture.projectDefaults.put("project-a", "agent-a");
        fixture.add("agent-a", 1, "project-a", AgentDefinitionLifecycle.PUBLISHED, true);
        fixture.add("agent-b", 1, "project-b", AgentDefinitionLifecycle.PUBLISHED, true);

        assertEquals("agent-a",
                fixture.service.resolveForProject(null, null, false, "project-a").agentId);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.resolveForProject("agent-b", null, false, "project-a"));
    }

    @Test
    void shouldRejectPlatformTemplateForProjectExecution() {
        Fixture fixture = new Fixture();
        fixture.add("template", 1, "", AgentDefinitionLifecycle.PUBLISHED, true);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.resolveForProject("template", null, false, "project-a"));
    }

    @Test
    void shouldFilterCurrentDefinitionsByProjectAndKeepVersionOrder() {
        Fixture fixture = new Fixture();
        fixture.add("agent-a", 1, "project-a", AgentDefinitionLifecycle.PUBLISHED, true);
        fixture.add("agent-a", 2, "project-a", AgentDefinitionLifecycle.DRAFT, false);
        fixture.add("agent-b", 1, "project-b", AgentDefinitionLifecycle.PUBLISHED, true);

        assertEquals(List.of("agent-a"), fixture.service.listForProject("project-a").stream()
                .map(definition -> definition.agentId)
                .toList());
        assertEquals(List.of(2, 1), fixture.service.versions("agent-a").stream()
                .map(definition -> definition.version)
                .toList());
    }

    private static final class Fixture implements
            AgentDefinitionCatalogPort<Definition>,
            AgentDefinitionDescriptorPort<Definition>,
            ProjectAgentDirectoryPort {

        private final Map<String, Definition> current = new LinkedHashMap<>();
        private final Map<String, List<Definition>> versions = new LinkedHashMap<>();
        private final Map<String, String> projectDefaults = new LinkedHashMap<>();
        private final AgentDefinitionQueryService<Definition> service =
                new AgentDefinitionQueryService<>(this, this, this);

        private Definition add(String agentId,
                               int version,
                               String projectId,
                               AgentDefinitionLifecycle lifecycle,
                               boolean currentVersion) {
            Definition definition = new Definition(
                    agentId, version, "hash-" + version, projectId, lifecycle);
            versions.computeIfAbsent(agentId, ignored -> new ArrayList<>()).add(0, definition);
            if (currentVersion) {
                current.put(agentId, definition);
            }
            return definition;
        }

        @Override
        public String defaultAgentId() {
            return "template";
        }

        @Override
        public Definition findCurrent(String agentId) {
            return current.get(agentId);
        }

        @Override
        public Definition findVersion(String agentId, int version) {
            return versions.getOrDefault(agentId, List.of()).stream()
                    .filter(item -> item.version == version)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<Definition> findCurrentDefinitions() {
            return new ArrayList<>(current.values());
        }

        @Override
        public List<Definition> findVersions(String agentId) {
            return new ArrayList<>(versions.getOrDefault(agentId, List.of()));
        }

        @Override
        public AgentDefinitionVersionState describe(Definition definition) {
            return new AgentDefinitionVersionState(
                    definition.agentId,
                    definition.version,
                    definition.definitionHash,
                    definition.projectId,
                    definition.lifecycle);
        }

        @Override
        public Definition snapshot(Definition definition) {
            return new Definition(
                    definition.agentId,
                    definition.version,
                    definition.definitionHash,
                    definition.projectId,
                    definition.lifecycle);
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean exists(String projectId) {
            return "project-a".equals(projectId) || "project-b".equals(projectId);
        }

        @Override
        public String defaultAgentId(String projectId) {
            return projectDefaults.get(projectId);
        }
    }

    private static final class Definition {
        private final String agentId;
        private final int version;
        private final String definitionHash;
        private final String projectId;
        private final AgentDefinitionLifecycle lifecycle;

        private Definition(String agentId,
                           int version,
                           String definitionHash,
                           String projectId,
                           AgentDefinitionLifecycle lifecycle) {
            this.agentId = agentId;
            this.version = version;
            this.definitionHash = definitionHash;
            this.projectId = projectId;
            this.lifecycle = lifecycle;
        }
    }
}
