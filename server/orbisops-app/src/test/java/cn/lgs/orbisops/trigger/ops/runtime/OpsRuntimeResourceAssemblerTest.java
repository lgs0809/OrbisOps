package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolProvider;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeResourceAssemblerTest {

    @Test
    void shouldRejectMissingExplicitModelWithoutChangingTheSelectedModel() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        AiClientModelCatalogPort modelDao = mock(AiClientModelCatalogPort.class);
        when(modelDao.findByModelId("missing-model")).thenReturn(null);

        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of());
        OpsRuntimeResourceAssembler assembler = assembler(
                modelResolver(
                        defaultChatModel,
                        modelDao,
                        mock(AiClientApiCatalogPort.class)),
                mcpResolver(mcpToolProvider),
                skillResolver(null),
                knowledgeResolver(null),
                builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());
        List<OpsRuntimeEvent> events = new ArrayList<>();

        var error = assertThrows(IllegalStateException.class, () -> assembler.assembleAgent(
                OpsAgentDefinition.builder()
                        .agentId("ops")
                        .engine("CHAT")
                        .modelId("missing-model")
                        .build(),
                OpsAgentChatRequest.builder().query("hello").build(),
                events,
                null));

        assertEquals("MODEL_BINDING_UNAVAILABLE", error.getMessage());
        org.mockito.Mockito.verifyNoInteractions(defaultChatModel);
    }

    @Test
    void shouldNotExposeAllSkillsWhenAgentHasNoSkillBindings() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsSkillToolProvider skillProvider = mock(OpsSkillToolProvider.class);
        OpsRuntimeResourceAssembler assembler = assembler(defaultChatModel, skillProvider);

        OpsRuntimeResourceBundle bundle = assembler.assembleAgent(
                OpsAgentDefinition.builder()
                        .agentId("ops")
                        .engine("CHAT")
                        .build(),
                OpsAgentChatRequest.builder().query("hello").build(),
                new ArrayList<>(),
                null);

        assertEquals(0, bundle.getTools().size());
        assertEquals(null, bundle.getSkillContext());
        verify(skillProvider, never()).buildSkillToolCallback(anyCollection());
        verify(skillProvider, never()).renderSkillContext(anyCollection(), anyInt());
        verify(skillProvider, never()).renderSkillSummaryContext(anyCollection(), anyInt());
    }

    @Test
    void configuredNamesMustNotBypassFrozenCatalogWithLiveFileTools() {
        ChatModel model=mock(ChatModel.class);
        OpsSkillToolProvider files=mock(OpsSkillToolProvider.class);
        var bundle=assembler(model,files).assembleAgent(
                OpsAgentDefinition.builder().agentId("ops").engine("CHAT").skills(List.of("es-log-agent")).build(),
                OpsAgentChatRequest.builder().query("traceId error").build(),new ArrayList<>(),null);
        assertTrue(bundle.getTools().isEmpty());assertEquals(null,bundle.getSkillContext());
        verify(files,never()).buildSkillToolCallback(anyCollection());
        verify(files,never()).renderSkillContext(anyCollection(),anyInt());
    }

    @Test
    void frozenCatalogStillExposesGovernedOnDemandSkillTool() {
        ChatModel model=mock(ChatModel.class);
        SkillCatalogQueryService catalog=mock(SkillCatalogQueryService.class);
        OpsProjectSkillToolProvider tools=mock(OpsProjectSkillToolProvider.class);
        List<Map<String,Object>> refs=List.of(Map.of("skillId","ops-fixture","scope","PROJECT","projectId","demo-project",
                "version",1,"skillHash","hash","packageHash","package","description","read logs"));
        when(catalog.retainRuntimeCatalogRefs(eq("demo-project"),eq(refs))).thenReturn(refs);
        ToolCallback frozenTool=tool("UseProjectSkill","frozen-body");
        when(tools.build(eq("demo-project"),eq("alice"),eq("run-1"),eq(refs))).thenReturn(frozenTool);
        var assembler=assembler(modelResolver(model,null,null),mcpResolver(mock(OpsMcpToolProvider.class)),
                skillResolver(null,catalog,null,null,tools),knowledgeResolver(null),builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),toolTraceDecorator(null),new OpsRuntimeResourceSummaryAuditor());
        var bundle=assembler.assembleAgentScope(OpsAgentDefinition.builder().agentId("ops").build(),
                OpsAgentScopeConfig.builder().agentId("platform-ops-react").inheritProjectCapabilities(true).build(),
                OpsAgentChatRequest.builder().projectId("demo-project").userId("alice").runId("run-1").query("read logs")
                        .metadata(new java.util.LinkedHashMap<>(Map.of("skillCatalogRefs",refs))).build(),new ArrayList<>(),null);
        assertEquals(1,bundle.getTools().size());
        assertEquals("frozen-body",bundle.getTools().get(0).call("{}"));
    }

    @Test
    void shouldMarkMcpToolTracePayload() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        ToolCallback mcpTool = governedReadOnlyTool("elk.search", "elk-result");
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of(mcpTool));
        OpsRuntimeResourceAssembler assembler = assembler(
                defaultChatModel,
                null,
                mcpToolProvider,
                knowledgeResolver(null),
                builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());
        List<OpsRuntimeEvent> events = new ArrayList<>();

        OpsRuntimeResourceBundle bundle = assembler.assembleAgent(
                OpsAgentDefinition.builder()
                        .agentId("ops")
                        .engine("CHAT")
                        .mcpServers(List.of(governedMcp("elk")))
                        .build(),
                OpsAgentChatRequest.builder().query("查日志").build(),
                events,
                null);

        assertEquals(1, bundle.getTools().size());
        assertEquals("elk-result", bundle.getTools().get(0).call("{\"traceId\":\"t1\"}"));
        List<Map<String, Object>> payloads = events.stream()
                .filter(event -> event.getEventType().startsWith("TOOL_CALL_"))
                .map(OpsRuntimeEvent::getPayload)
                .toList();
        assertEquals(2, payloads.size());
        assertTrue(payloads.stream().allMatch(payload -> "mcp".equals(payload.get("toolKind"))));
        assertTrue(payloads.stream().anyMatch(payload -> "elk.search".equals(payload.get("toolName"))));
    }

    @Test
    void shouldEnforceBuiltInSubAgentToolAllowlist() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        ToolCallback projectMcp = governedReadOnlyTool("project_mcp_logs", "logs");
        ToolCallback unsafeAdmin = governedReadOnlyTool("unsafe_admin", "unsafe");
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of(projectMcp, unsafeAdmin));
        OpsRuntimeResourceAssembler assembler = assembler(
                defaultChatModel,
                null,
                mcpToolProvider,
                knowledgeResolver(null),
                builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());

        OpsRuntimeResourceBundle bundle = assembler.assembleAgentScope(
                OpsAgentDefinition.builder().agentId("ops").build(),
                OpsAgentScopeConfig.builder()
                        .agentId("evidence")
                        .role("EVIDENCE_EXPLORER")
                        .maxDepth(1)
                        .mcpServers(List.of(governedMcp("logs")))
                        .build(),
                OpsAgentChatRequest.builder().projectId("p1").query("查日志").build(),
                new ArrayList<>(),
                null);

        assertEquals(List.of("project_mcp_logs"), bundle.getTools().stream()
                .map(ToolCallback::getToolDefinition)
                .map(ToolDefinition::name)
                .toList());
        assertEquals("EVIDENCE_EXPLORER", bundle.getMetadata().get("subAgentRole"));
        assertEquals(1, bundle.getMetadata().get("subAgentMaxDepth"));
    }

    @Test
    void shouldNotInheritAgentMcpIntoGraphNode() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsRuntimeResourceAssembler assembler = assembler(defaultChatModel, null);

        OpsRuntimeResourceBundle bundle = assembler.assembleNode(
                OpsAgentDefinition.builder()
                        .agentId("ops")
                        .mcpIds(List.of("global-mcp"))
                        .mcpServers(List.of(governedMcp("global-server")))
                        .build(),
                OpsWorkflowNode.builder()
                        .nodeId("elk")
                        .type("AGENT")
                        .agent("elk-agent")
                        .mcpIds(List.of("node-mcp"))
                        .mcpServers(List.of(governedMcp("node-server")))
                        .build(),
                OpsAgentChatRequest.builder().query("查日志").build(),
                new ArrayList<>(),
                null);

        assertEquals(List.of("node-mcp"), bundle.getMcpIds());
        assertEquals(1, bundle.getMcpServers().size());
        assertEquals("node-server", bundle.getMcpServers().get(0).getName());
    }

    @Test
    void shouldNotInheritAgentMcpIntoAgentScopeChild() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsRuntimeResourceAssembler assembler = assembler(defaultChatModel, null);

        OpsRuntimeResourceBundle bundle = assembler.assembleAgentScope(
                OpsAgentDefinition.builder()
                        .agentId("ops")
                        .mcpIds(List.of("global-mcp"))
                        .mcpServers(List.of(governedMcp("global-server")))
                        .build(),
                OpsAgentScopeConfig.builder()
                        .agentId("child")
                        .mcpIds(List.of("child-mcp"))
                        .mcpServers(List.of(governedMcp("child-server")))
                        .build(),
                OpsAgentChatRequest.builder().query("查日志").build(),
                new ArrayList<>(),
                null);

        assertEquals(List.of("child-mcp"), bundle.getMcpIds());
        assertEquals(1, bundle.getMcpServers().size());
        assertEquals("child-server", bundle.getMcpServers().get(0).getName());
    }

    @Test
    void shouldInheritOnlySelectedProjectCapabilitiesForPlatformAgent() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        ProjectSkillAuthorizationApplicationService skillAuthorization =
                mock(ProjectSkillAuthorizationApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfigService =
                mock(OpsProjectMcpRuntimeConfigService.class);
        when(skillAuthorization.enabledIds("payment")).thenReturn(List.of("payment-runbook"));
        when(authorization.enabledIds("payment")).thenReturn(List.of("payment-prometheus-mcp"));
        when(runtimeConfigService.resolve("payment", "payment-prometheus-mcp")).thenReturn(Optional.of(
                governedMcp("payment-prometheus-mcp")));
        OpsSkillToolProvider skillProvider = mock(OpsSkillToolProvider.class);
        when(skillProvider.buildSkillToolCallback(anyCollection())).thenReturn(Optional.empty());
        when(skillProvider.renderSkillSummaryContext(anyCollection(), anyInt())).thenReturn("- payment-runbook");
        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of());
        OpsRuntimeResourceAssembler assembler = assembler(
                modelResolver(defaultChatModel, null, null),
                mcpResolver(mcpToolProvider, runtimeConfigService, authorization),
                skillResolver(skillProvider, null, skillAuthorization, null, null),
                knowledgeResolver(null),
                builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());

        OpsRuntimeResourceBundle bundle = assembler.assembleAgentScope(
                OpsAgentDefinition.builder().agentId("generic-ops-react-agent").build(),
                OpsAgentScopeConfig.builder()
                        .agentId("platform-ops-react")
                        .inheritProjectCapabilities(true)
                        .build(),
                OpsAgentChatRequest.builder().projectId("payment").query("检查支付接口").build(),
                new ArrayList<>(),
                null);

        assertEquals(List.of("payment-runbook"), bundle.getSkillNames());
        assertEquals(List.of("payment-prometheus-mcp"), bundle.getMcpIds());
        verify(runtimeConfigService, never()).resolve(eq("demo-project"), anyString());
    }

    @Test
    void prepareAuthorityReactMustExposeProposalToolWithoutSeparateGovernanceRuntime() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        OpsChangePackageToolProvider proposalProvider =
                mock(OpsChangePackageToolProvider.class);
        OpsRuntimeResourceAssembler assembler = assembler(
                defaultChatModel,
                null,
                knowledgeResolver(null),
                builtInToolContributor(proposalProvider));
        ToolCallback proposalTool = tool("PrepareChangePackage", "{\"packageId\":\"cp_1\"}");
        ToolCallback queryTool = tool("QueryChangePackageStatus", "{\"items\":[]}");
        when(proposalProvider.buildStatusQuery(eq("demo-project"), eq("alice"), eq("run-1"), any(OpsAgentChatRequest.class)))
                .thenReturn(queryTool);
        when(proposalProvider.available("demo-project", List.of("demo-project-local-runtime"))).thenReturn(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        when(proposalProvider.build(
                eq("demo-project"),
                eq("alice"),
                eq("run-1"),
                anyString(),
                anyString(),
                same(events),
                eq(List.of("demo-project-local-runtime")),
                any(OpsAgentChatRequest.class)))
                .thenReturn(proposalTool);
        OpsAgentRunRequestDTO analysisRequest = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .requestedBy("alice")
                .build();

        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .projectId("demo-project")
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("report")
                .type("AGENT")
                .agent("reporter")
                .changePackageEnabled(true)
                .executionTargetIds(List.of("demo-project-local-runtime"))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("修复实例")
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest,
                        OpsAgentRunExecutionContextFactory.AUTHORITATIVE_KEY,
                        prepareAuthority("run-1", "demo-project"))))
                .build();

        OpsRuntimeResourceBundle bundle = assembler.assembleNode(
                definition, node, request, events, null);

        assertEquals(2, bundle.getTools().size());
        bundle.getTools().get(0).call("{}");
        assertEquals("change-package", events.stream()
                .filter(event -> "TOOL_CALL_FINISHED".equals(event.getEventType()))
                .findFirst()
                .map(OpsRuntimeEvent::getPayload)
                .map(payload -> payload.get("toolKind"))
                .orElse(null));
        verify(proposalProvider).build(
                eq("demo-project"),
                eq("alice"),
                eq("run-1"),
                anyString(),
                anyString(),
                same(events),
                eq(List.of("demo-project-local-runtime")),
                any(OpsAgentChatRequest.class));
    }

    @Test
    void shouldRenderCanaryOnlyFromContextBundleFrozenRef() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        SkillCatalogQueryService skillCatalogService = mock(SkillCatalogQueryService.class);
        OpsSkillReleaseService releaseService = mock(OpsSkillReleaseService.class);
        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of());
        OpsRuntimeResourceAssembler assembler = assembler(
                modelResolver(defaultChatModel, null, null),
                mcpResolver(mcpToolProvider),
                skillResolver(null, skillCatalogService, null, releaseService, null),
                knowledgeResolver(null),
                builtInToolContributor(null),
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());
        Map<String, Object> frozenRef = Map.of(
                "skillId", "candidate:candidate-1",
                "candidateId", "candidate-1",
                "releaseId", "release-1",
                "projectId", "project-a",
                "version", 5,
                "skillHash", "candidate-hash-1",
                "scope", "PROJECT",
                "statusAtUse", "CANARY");
        when(releaseService.renderFrozenCanaryContext(eq("project-a"), eq("agent-a"), anyList()))
                .thenReturn("frozen canary guidance");

        OpsRuntimeResourceBundle bundle = assembler.assembleAgent(
                OpsAgentDefinition.builder().agentId("agent-a").engine("CHAT").build(),
                OpsAgentChatRequest.builder()
                        .projectId("project-a")
                        .runId("run-a")
                        .query("diagnose")
                        .metadata(new java.util.LinkedHashMap<>(Map.of(
                                "usedSkillVersionRefs", List.of(frozenRef))))
                        .build(),
                new ArrayList<>(),
                null);

        assertTrue(bundle.getSkillContext().contains("frozen canary guidance"));
        verify(releaseService, atLeastOnce()).renderFrozenCanaryContext(
                eq("project-a"), eq("agent-a"), anyList());
        verify(releaseService, never()).canaryContext(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectKnowledgeBaseOutsideProjectAuthorization() {
        ChatModel defaultChatModel = mock(ChatModel.class);
        ProjectKnowledgeAuthorizationApplicationService authorizations =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        OpsRuntimeResourceAssembler assembler = assembler(
                defaultChatModel, null, knowledgeResolver(authorizations));
        when(authorizations.scope("project-a", "foreign-kb")).thenReturn("");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assembleAgent(
                        OpsAgentDefinition.builder()
                                .agentId("agent-a")
                                .projectId("project-a")
                                .engine("CHAT")
                                .ragEnabled(true)
                                .knowledgeBaseId("foreign-kb")
                                .build(),
                        OpsAgentChatRequest.builder()
                                .projectId("project-a")
                                .query("diagnose")
                                .build(),
                        new ArrayList<>(),
                        null));

        assertTrue(error.getMessage().contains("foreign-kb"));
        assertTrue(error.getMessage().contains("project-a"));
    }

    private static OpsRuntimeSkillResolver skillResolver(
            OpsSkillToolProvider skillToolProvider) {
        return skillResolver(skillToolProvider, null, null, null, null);
    }

    private static OpsRuntimeSkillResolver skillResolver(
            OpsSkillToolProvider skillToolProvider,
            SkillCatalogQueryService catalogQueryService,
            ProjectSkillAuthorizationApplicationService authorizationService,
            OpsSkillReleaseService releaseService,
            OpsProjectSkillToolProvider projectSkillToolProvider) {
        OpsRuntimeSkillSettings settings = OpsRuntimeSkillSettings.forTest(
                true, "lazy", 12000, 4000, 2500);
        OpsRuntimeFrozenSkillContextResolver frozenContextResolver =
                new OpsRuntimeFrozenSkillContextResolver(
                        () -> catalogQueryService,
                        () -> releaseService,
                        settings, mock(cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort.class));
        return new OpsRuntimeSkillResolver(
                () -> skillToolProvider,
                () -> authorizationService,
                () -> projectSkillToolProvider,
                frozenContextResolver,
                settings);
    }

    private static OpsRuntimeResourceAssembler assembler(ChatModel defaultChatModel, OpsSkillToolProvider skillProvider) {
        return assembler(defaultChatModel, skillProvider, knowledgeResolver(null));
    }

    private static OpsRuntimeResourceAssembler assembler(
            ChatModel defaultChatModel,
            OpsSkillToolProvider skillProvider,
            OpsRuntimeKnowledgeResolver knowledgeResolver) {
        return assembler(
                defaultChatModel,
                skillProvider,
                knowledgeResolver,
                builtInToolContributor(null));
    }

    private static OpsRuntimeResourceAssembler assembler(
            ChatModel defaultChatModel,
            OpsSkillToolProvider skillProvider,
            OpsRuntimeKnowledgeResolver knowledgeResolver,
            OpsRuntimeBuiltInToolContributor builtInToolContributor) {
        OpsMcpToolProvider mcpToolProvider = mock(OpsMcpToolProvider.class);
        when(mcpToolProvider.buildToolCallbacks(anyList())).thenReturn(List.of());
        return assembler(
                defaultChatModel,
                skillProvider,
                mcpToolProvider,
                knowledgeResolver,
                builtInToolContributor,
                new OpsRuntimeSubAgentToolBoundaryPolicy(),
                toolTraceDecorator(null),
                new OpsRuntimeResourceSummaryAuditor());
    }

    private static OpsRuntimeResourceAssembler assembler(ChatModel defaultChatModel,
                                                        OpsSkillToolProvider skillProvider,
                                                        OpsMcpToolProvider mcpToolProvider,
                                                        OpsRuntimeKnowledgeResolver knowledgeResolver,
                                                        OpsRuntimeBuiltInToolContributor builtInToolContributor,
                                                        OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy,
                                                        OpsRuntimeToolTraceDecorator toolTraceDecorator,
                                                        OpsRuntimeResourceSummaryAuditor summaryAuditor) {
        return assembler(
                modelResolver(defaultChatModel, null, null),
                mcpResolver(mcpToolProvider),
                skillResolver(skillProvider),
                knowledgeResolver,
                builtInToolContributor,
                subAgentToolBoundaryPolicy,
                toolTraceDecorator,
                summaryAuditor);
    }

    private static OpsRuntimeResourceAssembler assembler(
            OpsRuntimeModelResolver modelResolver,
            OpsRuntimeMcpResolver mcpResolver,
            OpsRuntimeSkillResolver skillResolver,
            OpsRuntimeKnowledgeResolver knowledgeResolver,
            OpsRuntimeBuiltInToolContributor builtInToolContributor,
            OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy,
            OpsRuntimeToolTraceDecorator toolTraceDecorator,
            OpsRuntimeResourceSummaryAuditor summaryAuditor) {
        OpsRuntimeResourceContextFactory contextFactory =
                new OpsRuntimeResourceContextFactory(skillResolver, mcpResolver);
        OpsRuntimeResourcePipeline pipeline = new OpsRuntimeResourcePipeline(
                modelResolver,
                mcpResolver,
                skillResolver,
                builtInToolContributor,
                knowledgeResolver,
                subAgentToolBoundaryPolicy,
                toolTraceDecorator,
                summaryAuditor);
        return new OpsRuntimeResourceAssembler(contextFactory, pipeline);
    }

    private static OpsRuntimeToolTraceDecorator toolTraceDecorator(
            OpsRunCancellationRegistry cancellationRegistry) {
        return new OpsRuntimeToolTraceDecorator(() -> cancellationRegistry);
    }

    private static OpsRuntimeBuiltInToolContributor builtInToolContributor(
            OpsChangePackageToolProvider changePackageToolProvider) {
        return new OpsRuntimeBuiltInToolContributor(
                () -> null,
                () -> null,
                () -> changePackageToolProvider,
                () -> null,
                () -> null);
    }

    private static OpsRuntimeKnowledgeResolver knowledgeResolver(
            ProjectKnowledgeAuthorizationApplicationService authorizationService) {
        return new OpsRuntimeKnowledgeResolver(() -> authorizationService);
    }

    private static OpsRuntimeMcpResolver mcpResolver(OpsMcpToolProvider toolProvider) {
        return mcpResolver(toolProvider, null, null);
    }

    private static OpsRuntimeMcpResolver mcpResolver(
            OpsMcpToolProvider toolProvider,
            OpsProjectMcpRuntimeConfigService runtimeConfigService,
            ProjectMcpAuthorizationApplicationService authorizationService) {
        OpsMcpRuntimeConfigSource source = new OpsMcpRuntimeConfigSource() {
            @Override
            public String sourceId() {
                return OpsProjectMcpRuntimeConfigSource.SOURCE_ID;
            }

            @Override
            public int order() {
                return OpsProjectMcpRuntimeConfigSource.ORDER;
            }

            @Override
            public OpsMcpRuntimeConfigSourceResult resolve(OpsMcpRuntimeConfigRequest request) {
                if (runtimeConfigService == null) {
                    return OpsMcpRuntimeConfigSourceResult.unavailable("TEST_RUNTIME_SERVICE_UNAVAILABLE");
                }
                Optional<OpsMcpServerConfig> configured = request.projectId().isBlank()
                        ? runtimeConfigService.resolveAny(request.mcpId())
                        : runtimeConfigService.resolve(request.projectId(), request.mcpId());
                return configured.map(OpsMcpRuntimeConfigSourceResult::match)
                        .orElseGet(() -> OpsMcpRuntimeConfigSourceResult.miss("TEST_RUNTIME_NOT_BOUND"));
            }
        };
        return new OpsRuntimeMcpResolver(
                toolProvider,
                new OpsMcpRuntimeConfigSourceChain(
                        List.of(source),
                        OpsMcpRuntimeConfigResolutionObserver.noop()),
                () -> authorizationService);
    }

    private static OpsRuntimeModelResolver modelResolver(
            ChatModel defaultChatModel,
            AiClientModelCatalogPort modelRepository,
            AiClientApiCatalogPort apiRepository) {
        return new OpsRuntimeModelResolver(
                () -> defaultChatModel,
                () -> defaultChatModel,
                () -> modelRepository,
                () -> apiRepository,
                mock(ModelAvailabilityPort.class),
                new OpsSecretResolver(mock(Environment.class)),
                OpsRuntimeModelSettings.forTest(5, 45));
    }

    private static ToolCallback tool(String name, String output) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.description()).thenReturn(name + " test tool");
        when(definition.inputSchema()).thenReturn("{\"type\":\"object\",\"properties\":{}}");
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(definition);
        when(tool.call(anyString())).thenReturn(output);
        return tool;
    }

    private static ToolCallback governedReadOnlyTool(String name, String output) {
        return OpsRuntimeGovernedToolCallback.wrap(
                tool(name, output),
                OpsRuntimeToolAuthorityDescriptor.readOnly(
                        "TEST_MCP",
                        Set.of(
                                AgentExecutionStage.INVESTIGATE,
                                AgentExecutionStage.PREPARE),
                        Set.of(name)));
    }

    private static AgentRunExecutionContext prepareAuthority(String runId, String projectId) {
        AgentSnapshot snapshot = new AgentSnapshot(
                "ops",
                1,
                "definition-hash",
                "prompt-hash",
                "model",
                Set.of("PrepareChangePackage"),
                Set.of(),
                Set.of(),
                Set.of());
        return new AgentRunExecutionContext(
                runId,
                runId,
                projectId,
                TriggerSource.CHAT,
                AgentExecutionStyle.REACT,
                AgentExecutionStage.PREPARE,
                snapshot,
                Optional.empty(),
                CapabilityProfile.TEST_FULL,
                Set.of("demo-project-local-runtime"),
                Set.of("PrepareChangePackage"),
                Instant.now().plusSeconds(60));
    }

    private static OpsMcpServerConfig governedMcp(String name) {
        return OpsMcpServerConfig.builder()
                .name(name)
                .mcpId(name)
                .toolId(name)
                .transport("stdio")
                .toolCapabilities(Map.of(
                        "resourceEnvironment", "prod",
                        "permissionProfile", "DATA_READONLY",
                        "allowedStages", "INVESTIGATE",
                        "readOnly", "true",
                        "riskLevel", "LOW",
                        "effectCeiling", "READ_ONLY"))
                .build();
    }
}
