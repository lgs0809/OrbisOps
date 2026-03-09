package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionCatalogLoadServiceTest {

    @Test
    void loadsSourcesInPrecedenceOrderAndFallsBackDefault() {
        TestModel model = new TestModel();
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        AgentDefinitionMutationService<TestDefinition> mutationService =
                new AgentDefinitionMutationService<>(model, new NoStore(), catalog);
        AgentDefinitionCatalogSourcePort<TestDefinition> source = new TestSource(
                List.of(
                        definition("yaml-agent", 1, "", "YAML"),
                        definition("project-agent", 1, "project-a", "YAML")),
                List.of(
                        definition("yaml-agent", 1, "", "YAML"),
                        definition("ui-agent", 2, "project-a", "UI")),
                List.of(
                        definition("yaml-agent", 1, "", "YAML"),
                        definition("ui-agent", 1, "project-a", "UI")));
        AgentDefinitionCatalogLoadService<TestDefinition> service =
                new AgentDefinitionCatalogLoadService<>(
                        source,
                        model,
                        () -> definition("fallback-agent", 1, "", "FALLBACK"),
                        mutationService,
                        catalog);

        AgentDefinitionCatalogLoadResult result = service.load(
                "classpath*:agents/*.yml",
                "missing-default",
                "fallback-agent");

        assertEquals("fallback-agent", result.effectiveDefaultAgentId());
        assertEquals(3, result.currentDefinitionCount());
        assertEquals(2, result.storedCurrentDefinitionCount());
        assertEquals(List.of("project-agent"), result.rejectedProjectPlatformAgentIds());
        assertEquals(List.of("yaml-agent"), result.skippedStoredYamlCurrentAgentIds());
        assertEquals(List.of("yaml-agent@1"), result.skippedStoredYamlVersionKeys());
        assertEquals("YAML", catalog.current("yaml-agent").source);
        assertEquals("UI", catalog.current("ui-agent").source);
        assertNotNull(catalog.current("fallback-agent"));
        assertEquals(List.of(2, 1), catalog.versions("ui-agent").stream()
                .map(item -> item.version)
                .toList());
        assertFalse(catalog.containsCurrent("project-agent"));
    }

    @Test
    void classpathDefaultIsNotOverwrittenByBuiltInFallbackVersion() {
        TestModel model = new TestModel();
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        AgentDefinitionMutationService<TestDefinition> mutationService =
                new AgentDefinitionMutationService<>(model, new NoStore(), catalog);
        AgentDefinitionCatalogLoadService<TestDefinition> service =
                new AgentDefinitionCatalogLoadService<>(
                        new TestSource(
                                List.of(definition("fallback-agent", 7, "", "YAML")),
                                List.of(),
                                List.of()),
                        model,
                        () -> definition("fallback-agent", 1, "", "FALLBACK"),
                        mutationService,
                        catalog);

        AgentDefinitionCatalogLoadResult result = service.load(
                "location",
                "fallback-agent",
                "fallback-agent");

        assertEquals("fallback-agent", result.effectiveDefaultAgentId());
        assertEquals(List.of(7), catalog.versions("fallback-agent").stream()
                .map(item -> item.version)
                .toList());
        assertEquals("YAML", catalog.current("fallback-agent").source);
    }

    @Test
    void requestedExistingDefaultWinsAndLoadClearsPreviousCatalog() {
        TestModel model = new TestModel();
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        catalog.register(definition("stale", 1, "", "UI"), true);
        AgentDefinitionMutationService<TestDefinition> mutationService =
                new AgentDefinitionMutationService<>(model, new NoStore(), catalog);
        AgentDefinitionCatalogLoadService<TestDefinition> service =
                new AgentDefinitionCatalogLoadService<>(
                        new TestSource(
                                List.of(definition("requested", 1, "", "YAML")),
                                List.of(),
                                List.of()),
                        model,
                        () -> definition("fallback-agent", 1, "", "FALLBACK"),
                        mutationService,
                        catalog);

        AgentDefinitionCatalogLoadResult result = service.load(
                "location",
                "requested",
                "fallback-agent");

        assertEquals("requested", result.effectiveDefaultAgentId());
        assertFalse(catalog.containsCurrent("stale"));
        assertTrue(catalog.containsCurrent("requested"));
        assertTrue(catalog.containsCurrent("fallback-agent"));
    }

    private TestDefinition definition(String agentId,
                                      int version,
                                      String projectId,
                                      String source) {
        return new TestDefinition(
                agentId,
                version,
                projectId,
                source,
                AgentDefinitionLifecycle.PUBLISHED,
                null,
                false);
    }

    private record TestSource(List<TestDefinition> platform,
                              List<TestDefinition> current,
                              List<TestDefinition> versions)
            implements AgentDefinitionCatalogSourcePort<TestDefinition> {

        @Override
        public List<TestDefinition> loadPlatformDefinitions(String locations) {
            return platform;
        }

        @Override
        public List<TestDefinition> loadStoredCurrentDefinitions() {
            return current;
        }

        @Override
        public List<TestDefinition> loadStoredVersionDefinitions() {
            return versions;
        }
    }

    private static final class TestModel implements
            AgentDefinitionCatalogLoadModelPort<TestDefinition>,
            AgentDefinitionMutationModelPort<TestDefinition>,
            AgentDefinitionDescriptorPort<TestDefinition> {

        @Override
        public String agentId(TestDefinition definition) {
            return definition == null ? null : definition.agentId;
        }

        @Override
        public Integer version(TestDefinition definition) {
            return definition == null ? null : definition.version;
        }

        @Override
        public String projectId(TestDefinition definition) {
            return definition == null ? null : definition.projectId;
        }

        @Override
        public String source(TestDefinition definition) {
            return definition == null ? null : definition.source;
        }

        @Override
        public String definitionHash(TestDefinition definition) {
            return definition == null ? null : definition.definitionHash;
        }

        @Override
        public boolean hasLifecycle(TestDefinition definition) {
            return definition != null && definition.lifecycle != null;
        }

        @Override
        public void assignVersion(TestDefinition definition, int version) {
            definition.version = version;
        }

        @Override
        public void assignLifecycle(TestDefinition definition,
                                    AgentDefinitionLifecycle lifecycle) {
            definition.lifecycle = lifecycle;
        }

        @Override
        public void assignSource(TestDefinition definition, String source) {
            definition.source = source;
        }

        @Override
        public void assignDefinitionHash(TestDefinition definition,
                                         String definitionHash) {
            definition.definitionHash = definitionHash;
        }

        @Override
        public void normalize(TestDefinition definition) {
            definition.normalized = true;
        }

        @Override
        public void validate(TestDefinition definition) {
        }

        @Override
        public TestDefinition snapshot(TestDefinition definition) {
            return new TestDefinition(definition);
        }

        @Override
        public String calculateHash(TestDefinition definition) {
            return definition.agentId + "@" + definition.version;
        }

        @Override
        public AgentDefinitionVersionState describe(TestDefinition definition) {
            return new AgentDefinitionVersionState(
                    definition.agentId,
                    definition.version,
                    definition.definitionHash,
                    definition.projectId,
                    definition.lifecycle);
        }
    }

    private static final class NoStore
            implements AgentDefinitionMutationStorePort<TestDefinition> {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public int maxVersion(String agentId) {
            return 0;
        }

        @Override
        public Optional<TestDefinition> findVersion(String agentId, int version) {
            return Optional.empty();
        }

        @Override
        public void saveVersion(TestDefinition definition, boolean currentPublished) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentDefinitionPublishResult publish(TestDefinition definition,
                                                    String expectedVersionHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean disableVersion(String agentId, int version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disableCurrent(String agentId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestDefinition {
        private final String agentId;
        private Integer version;
        private final String projectId;
        private String source;
        private AgentDefinitionLifecycle lifecycle;
        private String definitionHash;
        private boolean normalized;

        private TestDefinition(String agentId,
                               Integer version,
                               String projectId,
                               String source,
                               AgentDefinitionLifecycle lifecycle,
                               String definitionHash,
                               boolean normalized) {
            this.agentId = agentId;
            this.version = version;
            this.projectId = projectId;
            this.source = source;
            this.lifecycle = lifecycle;
            this.definitionHash = definitionHash;
            this.normalized = normalized;
        }

        private TestDefinition(TestDefinition source) {
            this(
                    source.agentId,
                    source.version,
                    source.projectId,
                    source.source,
                    source.lifecycle,
                    source.definitionHash,
                    source.normalized);
        }
    }
}
