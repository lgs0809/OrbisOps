package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import cn.lgs.orbisops.trigger.ops.OpsLlmDegradationException;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeContextBundleTest {

    @Test
    void missingRuntimeContextBundleServiceFailsClosedBeforeAdapter() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OpsWorkSessionContextPreparationService(
                        mock(OpsConversationMemoryService.class),
                        mock(OpsMainQuestionRewriteService.class),
                        null,
                        Optional.empty()));

        assertEquals("RUNTIME_CONTEXT_BUNDLE_SERVICE_REQUIRED", error.getMessage());
    }

    @Test
    void workSessionCreatesRuntimeContextBundleEvenWhenMemoryDisabled() {
        OpsRuntimeContextBundleAdapter bundleService = mock(OpsRuntimeContextBundleAdapter.class);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("contextBundleId", "rcb-1");
        bundle.put("contextBundleHash", "hash-1");
        bundle.put("memoryContextRefs", List.of());
        bundle.put("memoryContextHash", "");
        bundle.put("compressedMemorySummary", "");
        bundle.put("memoryInjectionVersion", "v1");
        bundle.put("usedSkillVersionRefs", List.of(Map.of("skillId", "skill-1", "version", 1, "skillHash", "hash-skill")));
        bundle.put("usedSkillRefsHash", "skill-refs-hash");
        bundle.put("toolsetRefs", List.of(Map.of("toolsetId", "mcp:project-1", "toolsetVersion", 1, "toolsetHash", "toolset-hash")));
        bundle.put("toolsetBoundaryHash", "toolset-boundary-hash");
        bundle.put("policyRefs", List.of(Map.of("policyId", "policy-1", "policyVersion", 1, "policyHash", "policy-hash")));
        bundle.put("policyHash", "policy-hash");
        bundle.put("runtimeBoundaryHash", "runtime-boundary-hash");
        bundle.put("approvalBoundaryHash", "approval-boundary-hash");
        when(bundleService.createBundle(any(), eq(""), any())).thenReturn(bundle);
        OpsMainQuestionRewriteService rewriteService = mock(OpsMainQuestionRewriteService.class);
        OpsWorkSessionContextPreparationService preparationService = preparationService(
                rewriteService, bundleService);
        OpsAgentChatRequest request = request();
        OpsRuntimeExecutionPlan plan = OpsRuntimeExecutionPlan.builder()
                .memoryEnabled(false)
                .metadata(new LinkedHashMap<>())
                .build();

        List<OpsRuntimeEvent> events = new ArrayList<>();
        preparationService.prepare(
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                request,
                plan,
                events,
                null,
                System.nanoTime());

        assertEquals("rcb-1", request.getMetadata().get("contextBundleId"));
        assertEquals("hash-1", request.getMetadata().get("contextBundleHash"));
        verify(rewriteService, never()).rewrite(any(), any());
        assertTrue(events.stream().anyMatch(event ->
                "QUERY_REWRITE_SKIPPED".equals(event.getEventType())
                        && String.valueOf(event.getPayload()).contains("NO_CONVERSATION_CONTEXT")));
        verify(bundleService).createBundle(any(), eq(""), any());
    }

    @Test
    void approvedLandingSkipsConversationMemoryAndQueryRewriteButStillCreatesBundle() {
        OpsRuntimeContextBundleAdapter bundleService = mock(OpsRuntimeContextBundleAdapter.class);
        when(bundleService.createBundle(any(), eq(""), any())).thenReturn(bundle());
        OpsMainQuestionRewriteService rewriteService = mock(OpsMainQuestionRewriteService.class);
        OpsConversationMemoryService memoryService = mock(OpsConversationMemoryService.class);
        OpsWorkSessionContextPreparationService preparationService =
                new OpsWorkSessionContextPreparationService(
                        memoryService, rewriteService, bundleService, Optional.empty());
        OpsAgentChatRequest request = request();
        request.setRunId("landing-run-1");
        request.setSessionId("landing-run-1");
        request.setAgentDefinitionId(OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID);
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "cp-1", 1, "hash-1", "project-1", "prod", "",
                Instant.now().plusSeconds(300));
        request.getMetadata().put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, approved);
        OpsAgentDefinition definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("project-1");
        new OpsAgentRunExecutionContextFactory().bindServerContext(request, definition);
        OpsRuntimeExecutionPlan plan = OpsRuntimeExecutionPlan.builder()
                .memoryEnabled(true)
                .metadata(new LinkedHashMap<>())
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        preparationService.prepare(
                definition, request, plan, events, null, System.nanoTime());

        assertEquals("check order errors", request.getQuery());
        assertEquals("", request.getMetadata().get("_runtimeMemoryContext"));
        assertEquals("check order errors", request.getMetadata().get("_rewrittenQuery"));
        assertTrue(events.stream().anyMatch(event ->
                "QUERY_REWRITE_SKIPPED".equals(event.getEventType())
                        && String.valueOf(event.getPayload()).contains("LANDING_TRUSTED_TASK_BOOK")));
        verify(memoryService, never()).assembleSelection(any(), any(), any(), any());
        verify(rewriteService, never()).rewrite(any(), any());
        verify(bundleService).createBundle(any(), eq(""), any());
    }

    @Test
    void queryRewriteLlmDegradationKeepsOriginalQuestion() {
        OpsMainQuestionRewriteService rewriteService = mock(OpsMainQuestionRewriteService.class);
        when(rewriteService.rewrite(eq("check order errors"), eq("memory")))
                .thenThrow(new OpsLlmDegradationException("ops-main-query-rewriter", "LLM unavailable"));
        OpsRuntimeContextBundleAdapter bundleService = mock(OpsRuntimeContextBundleAdapter.class);
        when(bundleService.createBundle(any(), eq("memory"), any())).thenReturn(bundle());
        OpsWorkSessionContextPreparationService preparationService = preparationService(rewriteService, bundleService);
        OpsAgentChatRequest request = request();
        OpsRuntimeExecutionPlan plan = OpsRuntimeExecutionPlan.builder()
                .memoryEnabled(true)
                .metadata(new LinkedHashMap<>())
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        preparationService.prepare(
                OpsAgentDefinition.builder().agentId("agent-1").queryRewriteEnabled(true).build(),
                request,
                plan,
                events,
                null,
                System.nanoTime());

        assertEquals("check order errors", request.getQuery());
        assertEquals("check order errors", request.getMetadata().get("_rewrittenQuery"));
        assertTrue(events.stream().anyMatch(event -> "QUERY_REWRITE_DEGRADED".equals(event.getEventType())));
    }

    @Test
    void modelCallWithoutAvailabilityFailsClosedBeforeRemoteCall() {
        OpsRuntimeLlmInvoker invoker = new OpsRuntimeLlmInvoker(null);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> invoker.call(
                        "system",
                        "user",
                        OpsRuntimeResourceBundle.builder().build(),
                        events,
                        null,
                        System.nanoTime()));

        assertTrue(error.getMessage().contains("模型可用性服务未初始化"));
        assertTrue(events.stream().anyMatch(event -> "MODEL_CALL_BLOCKED".equals(event.getEventType())));
    }

    private static Map<String, Object> bundle() {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("contextBundleId", "rcb-1");
        bundle.put("contextBundleHash", "hash-1");
        bundle.put("memoryContextRefs", List.of());
        bundle.put("memoryContextHash", "");
        bundle.put("compressedMemorySummary", "");
        bundle.put("memoryInjectionVersion", "v1");
        bundle.put("usedSkillVersionRefs", List.of(Map.of("skillId", "skill-1", "version", 1, "skillHash", "hash-skill")));
        bundle.put("usedSkillRefsHash", "skill-refs-hash");
        bundle.put("toolsetRefs", List.of(Map.of("toolsetId", "mcp:project-1", "toolsetVersion", 1, "toolsetHash", "toolset-hash")));
        bundle.put("toolsetBoundaryHash", "toolset-boundary-hash");
        bundle.put("policyRefs", List.of(Map.of("policyId", "policy-1", "policyVersion", 1, "policyHash", "policy-hash")));
        bundle.put("policyHash", "policy-hash");
        bundle.put("runtimeBoundaryHash", "runtime-boundary-hash");
        bundle.put("approvalBoundaryHash", "approval-boundary-hash");
        return bundle;
    }

    private static OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .sessionId("session-1")
                .userId("alice")
                .projectId("project-1")
                .agentDefinitionId("agent-1")
                .query("check order errors")
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private static OpsWorkSessionContextPreparationService preparationService(
            OpsMainQuestionRewriteService rewriteService,
            OpsRuntimeContextBundleAdapter bundleService) {
        OpsConversationMemoryService memoryService = mock(OpsConversationMemoryService.class);
        when(memoryService.assembleSelection(any(), any(), any(), any()))
                .thenReturn(new OpsMemorySelection("memory", List.of()));
        return new OpsWorkSessionContextPreparationService(
                memoryService,
                rewriteService,
                bundleService,
                Optional.empty());
    }
}
