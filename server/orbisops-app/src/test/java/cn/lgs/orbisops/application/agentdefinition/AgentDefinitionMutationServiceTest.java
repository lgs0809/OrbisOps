package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishConflict;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDefinitionMutationServiceTest {

    @Test
    void storedSnapshotMustRetainIdentityAndShapeAcrossApplicationUpdates() {
        TestModel model = new TestModel();
        AgentDefinitionMutationStorePort<TestDefinition> store = mock(AgentDefinitionMutationStorePort.class);
        var catalog = new AgentDefinitionMemoryCatalog<TestDefinition>(model);
        var service = new AgentDefinitionMutationService<TestDefinition>(model, store, catalog);
        var definition = new TestDefinition("stored", 2, AgentDefinitionLifecycle.PUBLISHED);
        definition.definitionHash = "persisted-before-new-default-fields";
        service.registerStoredSnapshot(definition, true);
        assertEquals("persisted-before-new-default-fields", catalog.current("stored").definitionHash);
        assertFalse(catalog.current("stored").normalized);
        assertEquals("persisted-before-new-default-fields", catalog.version("stored", 2).definitionHash);
    }

    @Test
    void unversionedLegacyAndPlatformDefinitionsStillReceiveIdentity() {
        TestModel model = new TestModel();
        AgentDefinitionMutationStorePort<TestDefinition> store = mock(AgentDefinitionMutationStorePort.class);
        var catalog = new AgentDefinitionMemoryCatalog<TestDefinition>(model);
        var service = new AgentDefinitionMutationService<TestDefinition>(model, store, catalog);
        var definition = new TestDefinition("legacy", null, null);
        service.registerStoredSnapshot(definition, true);
        assertEquals("hash-legacy-1", catalog.current("legacy").definitionHash);
        assertTrue(catalog.current("legacy").normalized);
    }

    @Test
    void draftUsesMaximumStoreVersionAndCommitsCatalogAfterPersistence() {
        TestModel model = new TestModel();
        @SuppressWarnings("unchecked")
        AgentDefinitionMutationStorePort<TestDefinition> store =
                mock(AgentDefinitionMutationStorePort.class);
        when(store.available()).thenReturn(true);
        when(store.maxVersion("agent-a")).thenReturn(4);
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        AgentDefinitionMutationService<TestDefinition> service =
                new AgentDefinitionMutationService<>(model, store, catalog);
        TestDefinition definition = new TestDefinition("agent-a", null, null);

        TestDefinition saved = service.saveDraft(definition, true);

        assertEquals(5, saved.version);
        assertEquals(AgentDefinitionLifecycle.DRAFT, saved.lifecycle);
        assertEquals("UI", saved.source);
        assertTrue(saved.normalized);
        assertEquals("hash-agent-a-5", saved.definitionHash);
        verify(store).saveVersion(any(TestDefinition.class), org.mockito.ArgumentMatchers.eq(false));
        assertEquals(5, catalog.version("agent-a", 5).version);
        assertNull(catalog.current("agent-a"));
    }

    @Test
    void persistenceFailureCannotCreateMemoryOnlyVersion() {
        TestModel model = new TestModel();
        @SuppressWarnings("unchecked")
        AgentDefinitionMutationStorePort<TestDefinition> store =
                mock(AgentDefinitionMutationStorePort.class);
        when(store.available()).thenReturn(true);
        doThrow(new IllegalStateException("write failed"))
                .when(store).saveVersion(any(), anyBoolean());
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        AgentDefinitionMutationService<TestDefinition> service =
                new AgentDefinitionMutationService<>(model, store, catalog);

        assertThrows(IllegalStateException.class,
                () -> service.saveDraft(new TestDefinition("agent-a", null, null), true));

        assertTrue(catalog.versions("agent-a").isEmpty());
    }

    @Test
    void disableAndDeleteChangeMemoryOnlyAfterStoreSuccess() {
        TestModel model = new TestModel();
        @SuppressWarnings("unchecked")
        AgentDefinitionMutationStorePort<TestDefinition> store =
                mock(AgentDefinitionMutationStorePort.class);
        when(store.available()).thenReturn(true);
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        catalog.register(new TestDefinition(
                "agent-a", 1, AgentDefinitionLifecycle.PUBLISHED), true);
        AgentDefinitionMutationService<TestDefinition> service =
                new AgentDefinitionMutationService<>(model, store, catalog);

        when(store.disableVersion("agent-a", 1)).thenReturn(false);
        assertFalse(service.disableVersion("agent-a", 1, true));
        assertEquals(AgentDefinitionLifecycle.PUBLISHED,
                catalog.current("agent-a").lifecycle);

        when(store.disableVersion("agent-a", 1)).thenReturn(true);
        assertTrue(service.disableVersion("agent-a", 1, true));
        assertNull(catalog.current("agent-a"));
        assertEquals(AgentDefinitionLifecycle.DISABLED,
                catalog.version("agent-a", 1).lifecycle);

        catalog.register(new TestDefinition(
                "agent-b", 1, AgentDefinitionLifecycle.PUBLISHED), true);
        doThrow(new IllegalStateException("disable current failed"))
                .when(store).disableCurrent("agent-b");
        assertThrows(IllegalStateException.class,
                () -> service.deleteCurrent("agent-b", "protected", true));
        assertEquals(AgentDefinitionLifecycle.PUBLISHED,
                catalog.current("agent-b").lifecycle);
    }

    @Test
    void publishConflictPropagatesBeforeCurrentCatalogCommit() {
        TestModel model = new TestModel();
        @SuppressWarnings("unchecked")
        AgentDefinitionMutationStorePort<TestDefinition> store =
                mock(AgentDefinitionMutationStorePort.class);
        when(store.available()).thenReturn(true);
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        TestDefinition validated = new TestDefinition(
                "agent-a", 1, AgentDefinitionLifecycle.VALIDATED);
        validated.definitionHash = "validated-hash";
        catalog.register(validated, false);
        AgentDefinitionMutationService<TestDefinition> service =
                new AgentDefinitionMutationService<>(model, store, catalog);
        AgentDefinitionPublishConflict conflict = new AgentDefinitionPublishConflict(
                AgentDefinitionPublishConflict.Reason.CURRENT_POINTER_CHANGED);
        doThrow(conflict).when(store).publish(any(), anyString());

        assertThrows(AgentDefinitionPublishConflict.class,
                () -> service.publishVersion("agent-a", 1, true));
        assertNull(catalog.current("agent-a"));
    }

    @Test
    void alreadyCurrentPublishStillRefreshesMemoryCatalog() {
        TestModel model = new TestModel();
        @SuppressWarnings("unchecked")
        AgentDefinitionMutationStorePort<TestDefinition> store =
                mock(AgentDefinitionMutationStorePort.class);
        when(store.available()).thenReturn(true);
        when(store.publish(any(), anyString()))
                .thenReturn(AgentDefinitionPublishResult.ALREADY_CURRENT);
        AgentDefinitionMemoryCatalog<TestDefinition> catalog =
                new AgentDefinitionMemoryCatalog<>(model);
        TestDefinition validated = new TestDefinition(
                "agent-a", 1, AgentDefinitionLifecycle.VALIDATED);
        validated.definitionHash = "validated-hash";
        catalog.register(validated, false);
        AgentDefinitionMutationService<TestDefinition> service =
                new AgentDefinitionMutationService<>(model, store, catalog);

        TestDefinition published = service.publishVersion("agent-a", 1, true);

        assertEquals(AgentDefinitionLifecycle.PUBLISHED, published.lifecycle);
        assertEquals(AgentDefinitionLifecycle.PUBLISHED,
                catalog.current("agent-a").lifecycle);
        verify(store).publish(any(), anyString());
    }

    private static final class TestModel implements
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
            if (!definition.normalized) {
                throw new IllegalStateException("definition must be normalized before validation");
            }
        }

        @Override
        public TestDefinition snapshot(TestDefinition definition) {
            return new TestDefinition(definition);
        }

        @Override
        public String calculateHash(TestDefinition definition) {
            return "hash-" + definition.agentId + "-" + definition.version;
        }

        @Override
        public AgentDefinitionVersionState describe(TestDefinition definition) {
            return new AgentDefinitionVersionState(
                    definition.agentId,
                    definition.version,
                    definition.definitionHash,
                    "",
                    definition.lifecycle);
        }
    }

    private static final class TestDefinition {
        private final String agentId;
        private Integer version;
        private AgentDefinitionLifecycle lifecycle;
        private String definitionHash;
        private String source;
        private boolean normalized;

        private TestDefinition(String agentId,
                               Integer version,
                               AgentDefinitionLifecycle lifecycle) {
            this.agentId = agentId;
            this.version = version;
            this.lifecycle = lifecycle;
        }

        private TestDefinition(TestDefinition source) {
            this.agentId = source.agentId;
            this.version = source.version;
            this.lifecycle = source.lifecycle;
            this.definitionHash = source.definitionHash;
            this.source = source.source;
            this.normalized = source.normalized;
        }
    }
}
