package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OpsRuntimeEventJournalTest {

    @Test
    void recordAddsEventAndDelegatesSameInstance() {
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        AtomicReference<OpsRuntimeEvent> delegated = new AtomicReference<>();
        OpsRuntimeEvent event = OpsRuntimeEvent.of("DONE", "SUCCEEDED", "完成");

        journal.record(events, delegated::set, event);

        assertEquals(List.of(event), events);
        assertSame(event, delegated.get());
    }

    @Test
    void requestStartTimestampRoundTripsThroughMetadata() {
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        OpsAgentChatRequest request = request("run-timing", new HashMap<>());

        journal.markRequestStarted(request, 123456789L);

        assertEquals(123456789L, journal.requestStartedNanos(request));
    }

    @Test
    void persistentSinkHeartbeatsPersistsCheckpointsAndDelegates() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        OpsAgentChatRequest request = request("run-1", new HashMap<>());
        AtomicReference<OpsRuntimeEvent> delegated = new AtomicReference<>();
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .nodeId("tool-node")
                .nodeType("TOOL")
                .agent("tool-agent")
                .summary("工具执行完成")
                .payload(Map.of("resultId", "result-1", "outputHash", "hash-1"))
                .build();

        journal.persistentSink(request, delegated::set).accept(event);

        verify(runService).heartbeat(request);
        verify(runService).checkpoint(eq(request), eq("RUNTIME_EVENT"), any());
        verify(graphEventService).publish(
                eq("run-1"), eq("session-1"), eq("TOOL_CALL_FINISHED"), any(),
                eq("SUCCEEDED"), eq("工具执行完成"), isNull(), isNull(), isNull(), any());
        assertSame(event, delegated.get());
    }

    @Test
    void persistentSinkSerializesConcurrentDurableWritesForOneRun() throws Exception {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        OpsAgentChatRequest request = request("run-concurrent", new HashMap<>());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            firstEntered.countDown();
            releaseFirst.await(2, TimeUnit.SECONDS);
            active.decrementAndGet();
            return null;
        }).when(runService).heartbeat(request);
        var sink = journal.persistentSink(request, null);
        OpsRuntimeEvent first = OpsRuntimeEvent.of("MODEL_CALL_FINISHED", "SUCCEEDED", "first");
        OpsRuntimeEvent second = OpsRuntimeEvent.of("MODEL_CALL_FINISHED", "SUCCEEDED", "second");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> left = executor.submit(() -> sink.accept(first));
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            Future<?> right = executor.submit(() -> sink.accept(second));
            Thread.sleep(50L);
            assertEquals(1, peak.get());
            releaseFirst.countDown();
            left.get();
            right.get();
            assertEquals(1, peak.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void analysisRuntimeSkipsDuplicateGraphEventPersistenceButKeepsCheckpoint() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        HashMap<String, Object> metadata = new HashMap<>();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setRunId("analysis-run");
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        OpsAgentChatRequest request = request(null, metadata);
        OpsRuntimeEvent event = OpsRuntimeEvent.of(
                "REVIEW_FINISHED", "SUCCEEDED", "复盘完成");

        journal.persistentSink(request, null).accept(event);

        verify(runService).heartbeat(request);
        verify(runService).checkpoint(eq(request), eq("RUNTIME_EVENT"), any());
        verify(graphEventService, never()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals("analysis-run", journal.canonicalRunId(request));
    }

    @Test
    void analysisRuntimeDurablyPersistsAuthoritativeSourceTraceExtension() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        HashMap<String, Object> metadata = new HashMap<>();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setRunId("analysis-run");
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY,
                OpsAnalysisResponseDTO.builder().analysisId("analysis-1").build());
        OpsAgentChatRequest request = request(null, metadata);
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .status("SUCCEEDED")
                .nodeId("execute")
                .nodeType("EXECUTE")
                .agent("prometheus-agent")
                .summary("PROMETHEUS authoritative datasource query succeeded")
                .payload(Map.of(
                        "sourceType", "PROMETHEUS",
                        "resultId", "result-1",
                        "outputHash", "hash-1"))
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(runService).heartbeat(request);
        verify(runService).checkpoint(eq(request), eq("RUNTIME_EVENT"), any());
        verify(graphEventService).publish(
                eq("analysis-run"), eq("analysis-1"), eq("SOURCE_QUERY_FINISHED"), any(),
                eq("SUCCEEDED"), eq("PROMETHEUS authoritative datasource query succeeded"),
                isNull(), isNull(), isNull(), any());
    }

    @Test
    void analysisRuntimeDurablyPersistsModelTimingTraceExtensions() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        HashMap<String, Object> metadata = new HashMap<>();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setRunId("analysis-run");
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY,
                OpsAnalysisResponseDTO.builder().analysisId("analysis-1").build());
        OpsAgentChatRequest request = request(null, metadata);
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_FINISHED")
                .status("SUCCEEDED")
                .nodeId("main-plan")
                .nodeType("PLAN")
                .agent("planner")
                .summary("模型调用完成。")
                .payload(Map.of("durationMs", 3210L, "promptChars", 12000))
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(graphEventService).publish(
                eq("analysis-run"), eq("analysis-1"), eq("MODEL_CALL_FINISHED"), any(),
                eq("SUCCEEDED"), eq("模型调用完成。"), isNull(), isNull(), isNull(), any());
    }

    @Test
    void analysisRuntimeDurablyPersistsEagerSkillActivationTrace() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(runService, graphEventService, () -> null);
        HashMap<String, Object> metadata = new HashMap<>();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setRunId("analysis-run");
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY,
                OpsAnalysisResponseDTO.builder().analysisId("analysis-1").build());
        OpsAgentChatRequest request = request(null, metadata);
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("SKILL_CONTEXT_LOADED")
                .status("SUCCEEDED")
                .nodeId("sub-agent-rag")
                .nodeType("SKILL_CONTEXT")
                .agent("rag-knowledge-agent")
                .source("rag")
                .summary("Eager Skill 上下文已注入：rag-knowledge-agent")
                .payload(Map.of("skillId", "rag-knowledge-agent", "mode", "EAGER"))
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(graphEventService).publish(
                eq("analysis-run"), eq("analysis-1"), eq("SKILL_CONTEXT_LOADED"), any(),
                eq("SUCCEEDED"), eq("Eager Skill 上下文已注入：rag-knowledge-agent"),
                isNull(), isNull(), isNull(), any());
    }

    @Test
    void analysisRuntimeDurablyPersistsResourceAssemblyTraceExtensions() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        HashMap<String, Object> metadata = new HashMap<>();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setRunId("analysis-run");
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY,
                OpsAnalysisResponseDTO.builder().analysisId("analysis-1").build());
        OpsAgentChatRequest request = request(null, metadata);

        for (String eventType : List.of(
                "REACT_AGENT_READY",
                "RUNTIME_RESOURCES",
                "MCP_CONFIG_RESOLVED",
                "MCP_CAPABILITY_BLOCKED",
                "BUSINESS_RESOURCE_IDENTITY_BLOCKED",
                "KNOWLEDGE_RETRIEVE_BLOCKED")) {
            journal.persistentSink(request, null).accept(OpsRuntimeEvent.builder()
                    .eventType(eventType)
                    .status("SUCCEEDED")
                    .summary(eventType + " audit")
                    .payload(Map.of("mcpIds", List.of("mcp-1")))
                    .build());
        }

        for (String eventType : List.of(
                "REACT_AGENT_READY",
                "RUNTIME_RESOURCES",
                "MCP_CONFIG_RESOLVED",
                "MCP_CAPABILITY_BLOCKED",
                "BUSINESS_RESOURCE_IDENTITY_BLOCKED",
                "KNOWLEDGE_RETRIEVE_BLOCKED")) {
            verify(graphEventService).publish(
                    eq("analysis-run"), eq("analysis-1"), eq(eventType), any(),
                    eq("SUCCEEDED"), eq(eventType + " audit"),
                    isNull(), isNull(), isNull(), any());
        }
    }

    @Test
    void oversizedRuntimePayloadIsBoundedBeforeGraphPersistence() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        OpsAgentChatRequest request = request("run-large-event", new HashMap<>());
        String oversized = "超".repeat(80_000);
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("QUERY_REWRITE_STARTED")
                .status("RUNNING")
                .nodeId("main-query-rewrite")
                .nodeType("QUERY_REWRITE")
                .agent("main-agent")
                .summary("rewrite")
                .payload(Map.of(
                        "originalQuestion", oversized,
                        "resultId", "result-1",
                        "outputHash", "hash-1"))
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(graphEventService).publish(
                eq("run-large-event"), eq("session-1"), eq("QUERY_REWRITE_STARTED"), any(),
                eq("RUNNING"), eq("rewrite"), isNull(), isNull(), isNull(),
                argThat(payload -> {
                    String json = com.alibaba.fastjson2.JSON.toJSONString(payload);
                    return json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 48_000
                            && Boolean.TRUE.equals(payload.get("_payloadTruncated"))
                            && "result-1".equals(payload.get("resultId"))
                            && "hash-1".equals(payload.get("outputHash"));
                }));
    }

    @Test
    void textDeltaOnlyHeartbeatsWithoutPersistenceOrCheckpoint() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> null);
        OpsAgentChatRequest request = request("run-delta", new HashMap<>());
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("TEXT_DELTA")
                .status("RUNNING")
                .content("片段")
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(runService).heartbeat(request);
        verify(runService, never()).checkpoint(any(), any(), any());
        verify(graphEventService, never()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void governanceAuditFailureDoesNotBlockRuntimeEventPersistence() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditService).recordRuntimeEvent(
                        any(), any(), any(), any(), any(), any(), any(), any(), any());
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                runService, graphEventService, () -> auditService);
        OpsAgentChatRequest request = request("run-audit", new HashMap<>());
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("工具完成")
                .payload(Map.of("toolCapability", "MUTATING"))
                .build();

        journal.persistentSink(request, null).accept(event);

        verify(graphEventService).publish(
                eq("run-audit"), eq("session-1"), eq("TOOL_CALL_FINISHED"), any(),
                eq("SUCCEEDED"), eq("工具完成"), isNull(), isNull(), isNull(), any());
    }

    @Test
    void runtimeCannotReclaimEventJournalProtocol() {
        Set<String> forbiddenMethods = Set.of(
                "record",
                "requestStartedNanos",
                "elapsedMs",
                "payloadWithElapsed",
                "persistentEventSink",
                "checkpointRuntimeEvent",
                "persistRuntimeEvent",
                "persistGovernanceAudit",
                "auditableRuntimeEvent",
                "runtimeAuditModule",
                "runtimeAuditRisk",
                "runtimeAuditActor",
                "runtimeAuditRunId");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .anyMatch(field -> "REQUEST_STARTED_NANOS_KEY".equals(field.getName())));
    }

    private OpsAgentChatRequest request(String runId, Map<String, Object> metadata) {
        return OpsAgentChatRequest.builder()
                .runId(runId)
                .projectId("project-1")
                .sessionId("session-1")
                .userId("user-1")
                .agentDefinitionId("agent-1")
                .metadata(metadata)
                .build();
    }
}
