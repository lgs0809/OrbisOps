package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Owns runtime event collection, durable checkpointing, event persistence and governance audit projection.
 */
@Slf4j
final class OpsRuntimeEventJournal {

    private static final String REQUEST_STARTED_NANOS_KEY = "_runtimeStartedNanos";
    private static final int PERSISTED_EVENT_PAYLOAD_MAX_BYTES = 48_000;
    private static final int PERSISTED_EVENT_FIELD_PREVIEW_CHARS = 512;
    private static final int PERSISTED_EVENT_MAX_FIELDS = 20;

    private final OpsWorkSessionRunAdapter workSessionRunService;
    private final GraphEventApplicationService graphEventService;
    private final Supplier<OpsConfigAuditService> configAuditServiceSupplier;
    private final OpsChannelRunProgressProjector channelRunProgressProjector;

    OpsRuntimeEventJournal(OpsWorkSessionRunAdapter workSessionRunService,
                           GraphEventApplicationService graphEventService,
                           Supplier<OpsConfigAuditService> configAuditServiceSupplier) {
        this(workSessionRunService, graphEventService, configAuditServiceSupplier, null);
    }

    OpsRuntimeEventJournal(OpsWorkSessionRunAdapter workSessionRunService,
                           GraphEventApplicationService graphEventService,
                           Supplier<OpsConfigAuditService> configAuditServiceSupplier,
                           OpsChannelRunProgressProjector channelRunProgressProjector) {
        this.workSessionRunService = workSessionRunService;
        this.graphEventService = graphEventService;
        this.configAuditServiceSupplier = configAuditServiceSupplier;
        this.channelRunProgressProjector = channelRunProgressProjector;
    }

    void markRequestStarted(OpsAgentChatRequest request, long startedNanos) {
        if (request != null && request.getMetadata() != null) {
            request.getMetadata().put(REQUEST_STARTED_NANOS_KEY, startedNanos);
        }
    }

    void record(List<OpsRuntimeEvent> events,
                Consumer<OpsRuntimeEvent> eventSink,
                OpsRuntimeEvent event) {
        events.add(event);
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    long requestStartedNanos(OpsAgentChatRequest request) {
        Object value = request == null || request.getMetadata() == null
                ? null
                : request.getMetadata().get(REQUEST_STARTED_NANOS_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return System.nanoTime();
    }

    long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    Map<String, Object> payloadWithElapsed(Map<String, Object> seed, long requestStartedNanos) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (seed != null) {
            payload.putAll(seed);
        }
        payload.put("elapsedMs", elapsedMs(requestStartedNanos));
        return payload;
    }

    Consumer<OpsRuntimeEvent> persistentSink(OpsAgentChatRequest request,
                                              Consumer<OpsRuntimeEvent> delegate) {
        Object persistenceMutex = new Object();
        return event -> {
            synchronized (persistenceMutex) {
                try {
                    // Custom LLM/sub-agent callbacks may arrive concurrently for
                    // one run. Serialize the durable journal boundary so heartbeat,
                    // graph event persistence and checkpoint sequence allocation do
                    // not race on the same Work Session row.
                    workSessionRunService.heartbeat(request);
                    persistRuntimeEvent(request, event);
                    checkpointRuntimeEvent(request, event);
                } catch (OpsRuntimePersistenceException error) {
                    throw error;
                } catch (RuntimeException error) {
                    throw new OpsRuntimePersistenceException(
                            "WORK_SESSION_RUNTIME_PERSISTENCE_FAILED", error);
                }
                if (channelRunProgressProjector != null) {
                    try {
                        channelRunProgressProjector.project(request, event);
                    } catch (RuntimeException projectionFailure) {
                        log.debug("Channel progress projection dispatch failed, runId={}, eventType={}, reason={}",
                                canonicalRunId(request), value(event == null ? null : event.getEventType()),
                                value(projectionFailure.getMessage()));
                    }
                }
                if (delegate != null) {
                    delegate.accept(event);
                }
            }
        };
    }

    String canonicalRunId(OpsAgentChatRequest request) {
        if (request != null && StringUtils.hasText(request.getRunId())) {
            return request.getRunId();
        }
        if (request == null) {
            return "";
        }
        Object analysisRequest = request.getMetadata() == null
                ? null
                : request.getMetadata().get(OpsAnalysisRuntimeMetadata.REQUEST_KEY);
        if (analysisRequest instanceof OpsAgentRunRequestDTO runRequest
                && StringUtils.hasText(runRequest.getRunId())) {
            return runRequest.getRunId();
        }
        return "";
    }

    private void checkpointRuntimeEvent(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        if (event == null || "TEXT_DELTA".equals(event.getEventType())) {
            return;
        }
        String eventType = value(event.getEventType()).toUpperCase(Locale.ROOT);
        if (!(eventType.endsWith("_STARTED")
                || eventType.endsWith("_FINISHED")
                || eventType.endsWith("_COMPLETED")
                || eventType.endsWith("_FAILED")
                || eventType.equals("RUNTIME_PLANNED")
                || eventType.equals("FINAL_OUTPUT")
                || eventType.equals("DONE"))) {
            return;
        }
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("eventType", eventType);
        checkpoint.put("status", value(event.getStatus()));
        checkpoint.put("nodeId", value(event.getNodeId()));
        checkpoint.put("nodeType", value(event.getNodeType()));
        checkpoint.put("agent", value(event.getAgent()));
        checkpoint.put("summary", OpsMemoryTextUtils.abbreviate(value(event.getSummary()), 1000));
        if (event.getPayload() != null) {
            checkpoint.put("refs", event.getPayload().entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("Id")
                            || entry.getKey().endsWith("Hash")
                            || entry.getKey().endsWith("Refs"))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (left, right) -> left,
                            LinkedHashMap::new)));
        }
        workSessionRunService.checkpoint(request, "RUNTIME_EVENT", checkpoint);
    }

    private void persistRuntimeEvent(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        if (request == null || event == null || "TEXT_DELTA".equals(event.getEventType())) {
            return;
        }
        persistGovernanceAudit(request, event);
        if (isAnalysisRequest(request)) {
            persistAnalysisExtensionEvent(request, event);
            return;
        }
        try {
            OpsWorkflowNode node = OpsWorkflowNode.builder()
                    .nodeId(OpsMemoryTextUtils.abbreviate(firstText(event.getNodeId(), event.getAgent(), event.getEventType()), 80))
                    .type(OpsMemoryTextUtils.abbreviate(firstText(event.getNodeType(), event.getEventType(), "EVENT"), 48))
                    .agent(OpsMemoryTextUtils.abbreviate(event.getAgent(), 80))
                    .build();
            Map<String, Object> payload = new LinkedHashMap<>();
            if (event.getPayload() != null) {
                payload.putAll(event.getPayload());
            }
            if (StringUtils.hasText(event.getContent())) {
                payload.put("content", OpsMemoryTextUtils.abbreviate(event.getContent(), 4000));
            }
            payload.put("sessionId", value(request.getSessionId()));
            payload.put("runId", canonicalRunId(request));
            payload.put("userId", value(request.getUserId()));
            payload = boundedPersistedPayload(payload);
            graphEventService.publish(
                    canonicalRunId(request),
                    request.getSessionId(),
                    OpsMemoryTextUtils.abbreviate(value(event.getEventType()), 128),
                    node,
                    OpsMemoryTextUtils.abbreviate(firstText(event.getStatus(), "RUNNING"), 32),
                    OpsMemoryTextUtils.abbreviate(firstText(event.getSummary(), event.getContent(), event.getEventType()), 8000),
                    null,
                    null,
                    null,
                    payload);
        } catch (Exception error) {
            Map<String, Object> eventPayload = event.getPayload();
            log.error(
                    "Work Session runtime event persistence failed, runId={}, sessionId={}, eventType={}, nodeIdLength={}, nodeTypeLength={}, agentLength={}, statusLength={}, summaryLength={}, contentLength={}, payloadKeys={}, rootCauseType={}, rootCauseMessage={}",
                    canonicalRunId(request),
                    value(request.getSessionId()),
                    value(event.getEventType()),
                    value(event.getNodeId()).length(),
                    value(event.getNodeType()).length(),
                    value(event.getAgent()).length(),
                    value(event.getStatus()).length(),
                    value(event.getSummary()).length(),
                    value(event.getContent()).length(),
                    eventPayload == null ? List.of() : eventPayload.keySet(),
                    rootCause(error).getClass().getName(),
                    value(rootCause(error).getMessage()));
            throw new OpsRuntimePersistenceException(
                    "Work Session 事件持久化失败，已阻断执行", error);
        }
    }

    /**
     * Analysis lifecycle events are already projected by OpsAnalysisRuntimeEventRecorder.
     * Trace extensions such as authoritative datasource evidence have no second durable
     * projection, so persist only that extension namespace here to avoid duplicates.
     */
    private void persistAnalysisExtensionEvent(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        String eventType = value(event == null ? null : event.getEventType()).toUpperCase(Locale.ROOT);
        if (!isAnalysisTraceExtension(eventType)) {
            return;
        }
        try {
            OpsWorkflowNode node = OpsWorkflowNode.builder()
                    .nodeId(firstText(event.getNodeId(), event.getAgent(), event.getEventType()))
                    .type(firstText(event.getNodeType(), event.getEventType(), "EVENT"))
                    .agent(event.getAgent())
                    .build();
            Map<String, Object> payload = new LinkedHashMap<>();
            if (event.getPayload() != null) {
                payload.putAll(event.getPayload());
            }
            if (StringUtils.hasText(event.getContent())) {
                payload.put("content", OpsMemoryTextUtils.abbreviate(event.getContent(), 4000));
            }
            payload.put("sessionId", value(request.getSessionId()));
            payload.put("runId", canonicalRunId(request));
            payload.put("userId", value(request.getUserId()));
            graphEventService.publish(
                    canonicalRunId(request),
                    analysisId(request),
                    value(event.getEventType()),
                    node,
                    firstText(event.getStatus(), "RUNNING"),
                    firstText(event.getSummary(), event.getContent(), event.getEventType()),
                    null,
                    null,
                    null,
                    payload);
        } catch (Exception error) {
            throw new OpsRuntimePersistenceException(
                    "Analysis 扩展事件持久化失败，已阻断执行", error);
        }
    }

    private boolean isAnalysisTraceExtension(String eventType) {
        return eventType.startsWith("SOURCE_QUERY_")
                || eventType.startsWith("TOOL_CALL_")
                || eventType.startsWith("MODEL_CALL_")
                || "MODEL_TTFT".equals(eventType)
                || "SKILL_CONTEXT_LOADED".equals(eventType)
                || "REACT_OUTCOME".equals(eventType)
                || "WORKFLOW_OUTCOME".equals(eventType)
                || "REACT_OUTCOME_MISSING".equals(eventType)
                || "REACT_AGENT_READY".equals(eventType)
                || "RUNTIME_RESOURCES".equals(eventType)
                || "MCP_CONFIG_RESOLVED".equals(eventType)
                || "MCP_CAPABILITY_BLOCKED".equals(eventType)
                || OpsBusinessResourceIdentityProjector.RESOLVED_EVENT.equals(eventType)
                || "BUSINESS_RESOURCE_IDENTITY_BLOCKED".equals(eventType)
                || "KNOWLEDGE_RETRIEVE_BLOCKED".equals(eventType)
                || eventType.startsWith("CHANGE_PACKAGE_")
                || "RESOURCE_WARN".equals(eventType);
    }

    private String analysisId(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) {
            return "";
        }
        Object response = request.getMetadata().get(OpsAnalysisRuntimeMetadata.RESPONSE_KEY);
        if (response instanceof OpsAnalysisResponseDTO analysisResponse
                && StringUtils.hasText(analysisResponse.getAnalysisId())) {
            return analysisResponse.getAnalysisId();
        }
        return value(request.getSessionId());
    }

    private void persistGovernanceAudit(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        OpsConfigAuditService configAuditService = configAuditServiceSupplier == null
                ? null
                : configAuditServiceSupplier.get();
        if (configAuditService == null || !auditableRuntimeEvent(event.getEventType())) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", value(request.getProjectId()));
            payload.put("agentId", value(request.getAgentDefinitionId()));
            payload.put("sessionId", value(request.getSessionId()));
            payload.put("nodeId", value(event.getNodeId()));
            payload.put("eventType", value(event.getEventType()));
            payload.put("summary", value(event.getSummary()));
            if (event.getPayload() != null) {
                payload.put("eventPayload", event.getPayload());
            }
            configAuditService.recordRuntimeEvent(
                    value(request.getProjectId()),
                    value(request.getAgentDefinitionId()),
                    runtimeAuditActor(request),
                    runtimeAuditModule(event.getEventType()),
                    value(event.getEventType()),
                    firstText(canonicalRunId(request), event.getNodeId()),
                    runtimeAuditRisk(event),
                    firstText(event.getStatus(), "UNKNOWN"),
                    payload);
        } catch (Exception error) {
            log.debug("统一运行审计写入失败，已保留原运行事件：{}", error.getMessage());
        }
    }

    private boolean isAnalysisRequest(OpsAgentChatRequest request) {
        return request != null
                && request.getMetadata() != null
                && request.getMetadata().containsKey(OpsAnalysisRuntimeMetadata.REQUEST_KEY);
    }

    private boolean auditableRuntimeEvent(String eventType) {
        String normalized = value(eventType).toUpperCase(Locale.ROOT);
        return normalized.startsWith("TOOL_CALL")
                || normalized.startsWith("ROUTE")
                || normalized.startsWith("ROUTER")
                || normalized.startsWith("REVIEW")
                || normalized.startsWith("REFLECT")
                || normalized.startsWith("CHANGE_PACKAGE")
                || "RUNTIME_PLANNED".equals(normalized);
    }

    private String runtimeAuditModule(String eventType) {
        String normalized = value(eventType).toUpperCase(Locale.ROOT);
        if (normalized.startsWith("TOOL_CALL")) {
            return "tool-call";
        }
        if (normalized.startsWith("CHANGE_PACKAGE")) {
            return "change-package";
        }
        return "agent-decision";
    }

    private String runtimeAuditRisk(OpsRuntimeEvent event) {
        String eventType = value(event == null ? null : event.getEventType()).toUpperCase(Locale.ROOT);
        Map<String, Object> payload = event == null || event.getPayload() == null
                ? Map.of()
                : event.getPayload();
        String capability = value(payload.get("toolCapability") == null
                ? null
                : String.valueOf(payload.get("toolCapability"))).toUpperCase(Locale.ROOT);
        if (eventType.startsWith("CHANGE_PACKAGE") || "MUTATING".equals(capability)) {
            return "HIGH";
        }
        if (eventType.startsWith("TOOL_CALL")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String runtimeAuditActor(OpsAgentChatRequest request) {
        Object analysisRequest = request.getMetadata() == null
                ? null
                : request.getMetadata().get(OpsAnalysisRuntimeMetadata.REQUEST_KEY);
        if (analysisRequest instanceof OpsAgentRunRequestDTO runRequest
                && StringUtils.hasText(runRequest.getRequestedBy())) {
            return runRequest.getRequestedBy();
        }
        return value(request.getUserId());
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String candidate : values) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private Map<String, Object> boundedPersistedPayload(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String serialized = JSON.toJSONString(safePayload);
        int originalBytes = serialized.getBytes(StandardCharsets.UTF_8).length;
        if (originalBytes <= PERSISTED_EVENT_PAYLOAD_MAX_BYTES) {
            return safePayload;
        }

        LinkedHashMap<String, Object> compact = new LinkedHashMap<>();
        List<String> priorityKeys = List.of(
                "sessionId", "runId", "userId", "operationId", "toolsetId", "toolName",
                "resultId", "toolResultId", "outputHash", "sourceType", "sourceId", "verified",
                "status", "reasonCode");
        for (String key : priorityKeys) {
            if (safePayload.containsKey(key) && compact.size() < PERSISTED_EVENT_MAX_FIELDS) {
                compact.put(key, persistedFieldPreview(safePayload.get(key)));
            }
        }
        for (Map.Entry<String, Object> entry : safePayload.entrySet()) {
            if (compact.size() >= PERSISTED_EVENT_MAX_FIELDS) {
                break;
            }
            if (!compact.containsKey(entry.getKey())) {
                compact.put(entry.getKey(), persistedFieldPreview(entry.getValue()));
            }
        }
        compact.put("_payloadTruncated", true);
        compact.put("_payloadOriginalBytes", originalBytes);
        compact.put("_payloadOriginalFieldCount", safePayload.size());
        return compact;
    }

    private Object persistedFieldPreview(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = value instanceof CharSequence
                ? String.valueOf(value)
                : JSON.toJSONString(value);
        return OpsMemoryTextUtils.abbreviate(text, PERSISTED_EVENT_FIELD_PREVIEW_CHARS);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? error : current;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
