package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionApplicationAssembly;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionApplicationFacade;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionRuntimeSettings;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionYamlLoader;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.testsupport.OpsAgentDefinitionValidatorTestFactory;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSourceLoader;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionTransactionPort;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionGraphRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishConflict;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionApplicationFacadeTest {

    @Test
    void yamlSourceLoaderFeedsOnlyPlatformTemplatesIntoRegistry() {
        AgentDefinitionSourceLoader<OpsAgentDefinition> loader = ignored -> List.of(
                OpsAgentDefinition.builder()
                        .agentId("yaml-platform")
                        .name("platform")
                        .engine("CHAT")
                        .build(),
                OpsAgentDefinition.builder()
                        .agentId("yaml-project")
                        .name("project")
                        .projectId("project-a")
                        .engine("CHAT")
                        .build());
        OpsAgentDefinitionApplicationFacade registry = registry(
                null, null, null, loader, null, "classpath*:ignored.yml", false);

        registry.load();

        assertNotNull(registry.findCurrent("yaml-platform"));
        assertNull(registry.findCurrent("yaml-project"));
    }

    @Test
    void shouldKeepVersionSnapshotsWhenSavingSameAgent() {
        OpsAgentDefinitionApplicationFacade registry = registry();

        OpsAgentDefinition first = registry.save(OpsAgentDefinition.builder()
                .agentId("ops-versioned")
                .name("first")
                .engine("CHAT")
                .build());
        OpsAgentDefinition second = registry.save(OpsAgentDefinition.builder()
                .agentId("ops-versioned")
                .name("second")
                .engine("CHAT")
                .build());

        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());
        assertEquals("first", registry.resolve("ops-versioned", 1, false).getName());
        assertEquals("second", registry.resolve("ops-versioned").getName());
        assertNotNull(first.getDefinitionHash());
        assertNotEquals(first.getDefinitionHash(), second.getDefinitionHash());
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("ops-versioned", 99, false));
    }

    @Test
    void shouldNotSwitchCurrentDefinitionWhenSavingDraft() {
        OpsAgentDefinitionApplicationFacade registry = registry();

        registry.save(OpsAgentDefinition.builder()
                .agentId("ops-draft")
                .name("published")
                .engine("CHAT")
                .build());
        OpsAgentDefinition draft = registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-draft")
                .name("draft")
                .engine("CHAT")
                .build());

        assertEquals(2, draft.getVersion());
        assertEquals("DRAFT", draft.getLifecycle());
        assertEquals("published", registry.resolve("ops-draft").getName());
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("ops-draft", 2, false));
        assertEquals("draft", registry.resolve("ops-draft", 2, true).getName());
    }

    @Test
    void shouldPublishValidatedVersionAsCurrent() {
        OpsAgentDefinitionApplicationFacade registry = registry();

        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-publish")
                .name("draft")
                .engine("CHAT")
                .build());
        OpsAgentDefinition validated = registry.validateVersion("ops-publish", 1);
        OpsAgentDefinition published = registry.publishVersion("ops-publish", 1);

        assertEquals("VALIDATED", validated.getLifecycle());
        assertEquals("PUBLISHED", published.getLifecycle());
        assertEquals("draft", registry.resolve("ops-publish").getName());
        assertEquals(validated.getDefinitionHash(), published.getDefinitionHash());
    }

    @Test
    void currentSaveAndVersionPublishShareTransactionPortBoundary() {
        AgentDefinitionTransactionPort transactionPort =
                mock(AgentDefinitionTransactionPort.class);
        when(transactionPort.required(any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        OpsAgentDefinitionApplicationFacade registry =
                registry(null, null, null, transactionPort);

        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-transaction")
                .name("draft")
                .engine("CHAT")
                .build());
        registry.validateVersion("ops-transaction", 1);
        registry.publishVersion("ops-transaction", 1);

        verify(transactionPort, org.mockito.Mockito.atLeast(3)).required(any());
    }

    @Test
    void draftPersistenceFailureIsNotDowngradedToMemoryOnlyState() {
        IAgentDefinitionRepository repository = mock(IAgentDefinitionRepository.class);
        when(repository.available()).thenReturn(true);
        IAgentCapabilityBindingRepository capabilityRepository = mock(IAgentCapabilityBindingRepository.class);
        when(capabilityRepository.available()).thenReturn(true);
        doThrow(new DataIntegrityViolationException("capability write failed"))
                .when(capabilityRepository)
                .replace(any());
        OpsAgentDefinitionApplicationFacade registry = registry(repository, null, capabilityRepository);

        assertThrows(DataIntegrityViolationException.class,
                () -> registry.saveDraft(OpsAgentDefinition.builder()
                        .agentId("ops-draft-fail")
                        .name("draft")
                        .engine("CHAT")
                        .skills(List.of("diagnosis"))
                        .build()));
        assertTrue(registry.listVersions("ops-draft-fail").isEmpty());
    }

    @Test
    void successfulPublishPersistsTypedNormalizedGraph() {
        IAgentDefinitionRepository repository = mock(IAgentDefinitionRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.publish(any(), anyString())).thenReturn(AgentDefinitionPublishResult.PUBLISHED);
        IAgentDefinitionGraphRepository graphRepository = mock(IAgentDefinitionGraphRepository.class);
        when(graphRepository.available()).thenReturn(true);
        IAgentCapabilityBindingRepository capabilityRepository = mock(IAgentCapabilityBindingRepository.class);
        when(capabilityRepository.available()).thenReturn(true);
        OpsAgentDefinitionApplicationFacade registry = registry(
                repository,
                graphRepository,
                capabilityRepository,
                new OpsAgentDefinitionYamlLoader(),
                null,
                "classpath*:/agents/ops/*.yml,classpath*:/agents/ops/*.yaml",
                true);
        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-graph")
                .name("graph")
                .engine("GRAPH")
                .startNodeId("start")
                .skills(List.of("diagnosis"))
                .nodes(List.of(
                        OpsWorkflowNode.builder()
                                .nodeId("start")
                                .type("START")
                                .build(),
                        OpsWorkflowNode.builder()
                                .nodeId("diagnose")
                                .type("CHAT")
                                .agent("diagnose-agent")
                                .build(),
                        OpsWorkflowNode.builder()
                                .nodeId("end")
                                .type("END")
                                .build()))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("start")
                                .to("diagnose")
                                .conditionType("always")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("diagnose")
                                .to("end")
                                .conditionType("always")
                                .build()))
                .build());
        registry.validateVersion("ops-graph", 1);

        registry.publishVersion("ops-graph", 1);

        verify(graphRepository).replace(argThat(snapshot ->
                "ops-graph".equals(snapshot.agentId())
                        && snapshot.nodes().size() == 3
                        && snapshot.skillBindings().size() == 1));
        verify(capabilityRepository).replace(argThat(snapshot ->
                "ops-graph".equals(snapshot.agentId())
                        && snapshot.version() == 1
                        && "PUBLISHED".equals(snapshot.lifecycle().name())
                        && snapshot.bindings().stream().anyMatch(binding ->
                        "diagnosis".equals(binding.capabilityId()))));
    }

    @Test
    void concurrentPublishCannotOverwriteChangedCurrentPointer() {
        IAgentDefinitionRepository repository = mock(IAgentDefinitionRepository.class);
        when(repository.available()).thenReturn(true);
        IAgentDefinitionGraphRepository graphRepository = mock(IAgentDefinitionGraphRepository.class);
        when(graphRepository.available()).thenReturn(true);
        IAgentCapabilityBindingRepository capabilityRepository = mock(IAgentCapabilityBindingRepository.class);
        when(capabilityRepository.available()).thenReturn(true);
        OpsAgentDefinitionApplicationFacade registry = registry(repository, graphRepository, capabilityRepository);
        registry.save(OpsAgentDefinition.builder()
                .agentId("ops-cas")
                .name("published")
                .engine("CHAT")
                .build());
        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-cas")
                .name("candidate")
                .engine("CHAT")
                .build());
        registry.validateVersion("ops-cas", 2);
        org.mockito.Mockito.doThrow(new AgentDefinitionPublishConflict(
                        AgentDefinitionPublishConflict.Reason.CURRENT_POINTER_CHANGED))
                .when(repository)
                .publish(any(), anyString());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.publishVersion("ops-cas", 2));

        assertEquals("AGENT_PUBLISH_MVCC_CONFLICT：当前发布版本已变化", error.getMessage());
    }

    @Test
    void changedCandidateKeepsCompatiblePublishError() {
        IAgentDefinitionRepository repository = mock(IAgentDefinitionRepository.class);
        when(repository.available()).thenReturn(true);
        IAgentCapabilityBindingRepository capabilityRepository = mock(IAgentCapabilityBindingRepository.class);
        when(capabilityRepository.available()).thenReturn(true);
        OpsAgentDefinitionApplicationFacade registry = registry(repository, null, capabilityRepository);
        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-version-cas")
                .name("candidate")
                .engine("CHAT")
                .build());
        registry.validateVersion("ops-version-cas", 1);
        org.mockito.Mockito.doThrow(new AgentDefinitionPublishConflict(
                        AgentDefinitionPublishConflict.Reason.VERSION_CHANGED))
                .when(repository)
                .publish(any(), anyString());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.publishVersion("ops-version-cas", 1));

        assertEquals("AGENT_VERSION_PUBLISH_CAS_FAILED：候选版本已变化或未完成校验", error.getMessage());
    }

    @Test
    void shouldRejectRequestedVersionWhenItIsOnlyValidated() {
        OpsAgentDefinitionApplicationFacade registry = registry();

        registry.save(OpsAgentDefinition.builder()
                .agentId("ops-stale-session")
                .name("published")
                .engine("CHAT")
                .build());
        registry.saveDraft(OpsAgentDefinition.builder()
                .agentId("ops-stale-session")
                .name("validated")
                .engine("CHAT")
                .build());
        registry.validateVersion("ops-stale-session", 2);

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("ops-stale-session", 2, false));
    }

    @Test
    void shouldDisableCurrentVersion() {
        OpsAgentDefinitionApplicationFacade registry = registry();

        registry.save(OpsAgentDefinition.builder()
                .agentId("ops-disabled")
                .name("published")
                .engine("CHAT")
                .build());
        registry.disableVersion("ops-disabled", 1);

        assertThrows(IllegalArgumentException.class, () -> registry.resolve("ops-disabled"));
        assertEquals("DISABLED", registry.listVersions("ops-disabled").get(0).getLifecycle());
    }

    @Test
    void shouldLoadQueryRewriteSettingFromYamlDefinition() {
        OpsAgentDefinitionApplicationFacade registry = registry(
                null,
                null,
                null,
                new OpsAgentDefinitionYamlLoader(),
                null,
                "classpath*:/agents/ops/generic-ops-react-agent.yml",
                false);

        registry.load();

        OpsAgentDefinition definition = registry.resolve("generic-ops-react-agent");
        assertEquals(Boolean.TRUE, definition.getQueryRewriteEnabled());
        assertEquals("AGENTSCOPE", definition.getEngine());
        assertEquals(null, definition.getModelId());
        assertEquals(1, definition.getAgentscopeAgents().size());
        assertEquals(List.of("MAIN_ASSISTANT"),
                definition.getAgentscopeAgents().stream().map(OpsAgentScopeConfig::getRole).toList());
        assertTrue(definition.getAgentscopeAgents().stream().allMatch(agent -> Integer.valueOf(1).equals(agent.getMaxDepth())));
        assertTrue(definition.getAgentscopeAgents().stream().allMatch(agent -> !agent.getAllowedToolNames().isEmpty()));
    }

    @Test
    void shouldRequireProjectOwnedAgentAndRejectCrossProjectAgent() {
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        when(definitions.exists("payment")).thenReturn(true);
        when(definitions.defaultAgentId("payment")).thenReturn("payment-ops-agent");
        OpsAgentDefinitionApplicationFacade registry = registry(
                null,
                null,
                null,
                new OpsAgentDefinitionYamlLoader(),
                definitions,
                "classpath*:/agents/ops/*.yml",
                false);
        registry.load();

        assertThrows(IllegalArgumentException.class, () -> registry.resolve("demo-ops-agent"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolveForProject("generic-ops-react-agent", null, false, "payment"));
        registry.save(OpsAgentDefinition.builder()
                .agentId("payment-ops-agent")
                .projectId("payment")
                .name("payment")
                .engine("CHAT")
                .build());
        assertEquals("payment-ops-agent",
                registry.resolveForProject(null, null, false, "payment").getAgentId());
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolveForProject("demo-ops-agent", null, false, "payment"));
    }

    private OpsAgentDefinitionApplicationFacade registry() {
        return registry(null, null, null);
    }

    private OpsAgentDefinitionApplicationFacade registry(
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository) {
        return registry(
                definitionRepository,
                graphRepository,
                capabilityRepository,
                directTransactionPort());
    }

    private OpsAgentDefinitionApplicationFacade registry(
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository,
            AgentDefinitionTransactionPort transactionPort) {
        return registry(
                definitionRepository,
                graphRepository,
                capabilityRepository,
                new OpsAgentDefinitionYamlLoader(),
                null,
                "classpath*:/agents/ops/*.yml,classpath*:/agents/ops/*.yaml",
                false,
                transactionPort);
    }

    private OpsAgentDefinitionApplicationFacade registry(
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository,
            AgentDefinitionSourceLoader<OpsAgentDefinition> sourceLoader,
            ProjectDefinitionApplicationService projectDefinitionService,
            String locations,
            boolean jdbcEnabled) {
        return registry(
                definitionRepository,
                graphRepository,
                capabilityRepository,
                sourceLoader,
                projectDefinitionService,
                locations,
                jdbcEnabled,
                directTransactionPort());
    }

    private OpsAgentDefinitionApplicationFacade registry(
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository,
            AgentDefinitionSourceLoader<OpsAgentDefinition> sourceLoader,
            ProjectDefinitionApplicationService projectDefinitionService,
            String locations,
            boolean jdbcEnabled,
            AgentDefinitionTransactionPort transactionPort) {
        OpsAgentDefinitionRuntimeSettings settings = OpsAgentDefinitionRuntimeSettings.forTest(
                locations,
                OpsAgentDefinitionApplicationFacade.DEFAULT_AGENT_ID,
                jdbcEnabled);
        OpsAgentDefinitionApplicationAssembly assembly = OpsAgentDefinitionApplicationAssembly.create(
                OpsAgentDefinitionValidatorTestFactory.create(null, testSkillCatalog()),
                definitionRepository,
                graphRepository,
                capabilityRepository,
                sourceLoader,
                new OpsAgentDefinitionSnapshotMapper(),
                () -> projectDefinitionService,
                settings::effectiveDefaultAgentId);
        return new OpsAgentDefinitionApplicationFacade(
                assembly,
                settings,
                transactionPort);
    }

    private cn.lgs.orbisops.application.skill.SkillCatalogPort testSkillCatalog() {
        // This graph-persistence fixture binds one synthetic global Skill; it is not model evidence.
        var catalog = mock(cn.lgs.orbisops.application.skill.SkillCatalogPort.class);
        java.util.Map<String,Object> view = new java.util.LinkedHashMap<>();
        view.put("skillId", "diagnosis"); view.put("scope", "GLOBAL"); view.put("projectId", "");
        view.put("name", "diagnosis"); view.put("description", "synthetic diagnosis method");
        view.put("status", "ENABLED"); view.put("version", 1); view.put("skillHash", "synthetic-hash");
        view.put("whenToUse", List.of("diagnosis")); view.put("whenNotToUse", List.of("production write"));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(
                cn.lgs.orbisops.application.skill.SkillCatalogSnapshot.fromView(view)));
        return catalog;
    }

    private AgentDefinitionTransactionPort directTransactionPort() {
        return new AgentDefinitionTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
