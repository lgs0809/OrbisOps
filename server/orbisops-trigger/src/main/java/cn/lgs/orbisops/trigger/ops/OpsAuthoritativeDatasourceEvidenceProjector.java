package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeResourceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects a durable ToolResult/Evidence reference into the runtime event
 * journal. Model-generated observations are never accepted as datasource evidence.
 */
@Service
public class OpsAuthoritativeDatasourceEvidenceProjector {

    public static final String EVENT_TYPE = "SOURCE_QUERY_FINISHED";
    public static final String PROMETHEUS = "PROMETHEUS";
    public static final String ELASTICSEARCH = "ELASTICSEARCH";
    public static final String MYSQL_SLOW_SQL = "MYSQL_SLOW_SQL";

    private final OpsAuthoritativeDatasourceEvidenceStore store;

    OpsAuthoritativeDatasourceEvidenceProjector() {
        this.store = null;
    }

    @Autowired
    OpsAuthoritativeDatasourceEvidenceProjector(OpsAuthoritativeDatasourceEvidenceStore store) {
        this.store = store;
    }

    static Map<String, Object> summary(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (keyValues == null) return result;
        if ((keyValues.length & 1) != 0) throw new IllegalArgumentException("DATASOURCE_SUMMARY_KEY_VALUE_PAIRS_REQUIRED");
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    public void record(OpsAgentRunRequestDTO request,
                       String sourceType,
                       String source,
                       Map<String, Object> structuredSummary) {
        OpsLlmTraceContext.Trace trace = OpsLlmTraceContext.current();
        if (trace == null) return;
        OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence persisted =
                persist(request, sourceType, source, structuredSummary);
        if (persisted == null) return;
        trace.record(event(
                sourceType,
                source,
                persisted,
                trace.nodeId(),
                trace.nodeType(),
                trace.agent(),
                trace.source()));
    }

    public void record(OpsAgentRunRequestDTO request,
                       String sourceType,
                       String source,
                       Map<String, Object> structuredSummary,
                       OpsRuntimeResourceContext runtimeContext) {
        if (runtimeContext == null) {
            record(request, sourceType, source, structuredSummary);
            return;
        }
        OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence persisted =
                persist(request, sourceType, source, structuredSummary);
        if (persisted == null) return;
        String nodeId = runtimeContext.getNode() == null
                ? runtimeContext.getAgentScope() == null ? "" : text(runtimeContext.getAgentScope().getAgentId())
                : text(runtimeContext.getNode().getNodeId());
        String nodeType = runtimeContext.getNode() == null
                ? runtimeContext.getAgentScope() == null ? "" : "AGENTSCOPE"
                : text(runtimeContext.getNode().getType());
        String agent = runtimeContext.getNode() == null
                ? runtimeContext.getAgentScope() == null ? "" : text(runtimeContext.getAgentScope().getAgentId())
                : text(runtimeContext.getNode().getAgent());
        runtimeContext.record(event(
                sourceType,
                source,
                persisted,
                nodeId,
                nodeType,
                agent,
                ""));
    }

    private OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence persist(
            OpsAgentRunRequestDTO request,
            String sourceType,
            String source,
            Map<String, Object> structuredSummary) {
        if (store == null || request == null
                || text(request.getRunId()).isBlank()
                || text(sourceType).isBlank()
                || text(source).isBlank()) {
            return null;
        }
        return store.persist(request, sourceType, source, structuredSummary);
    }

    private OpsRuntimeEvent event(
            String sourceType,
            String source,
            OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence persisted,
            String nodeId,
            String nodeType,
            String agent,
            String runtimeSource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", sourceType.trim());
        payload.put("source", source.trim());
        payload.put("resultId", persisted.resultId());
        payload.put("evidenceId", persisted.evidenceId());
        payload.put("outputHash", persisted.outputHash());
        payload.put("fullOutputRef", persisted.fullOutputRef());
        payload.put("verified", true);
        payload.put("observedAt", persisted.observedAt());
        payload.put("structuredSummary", persisted.structuredSummary());
        return OpsRuntimeEvent.builder()
                .eventType(EVENT_TYPE)
                .nodeId(nodeId)
                .nodeType(nodeType)
                .agent(agent)
                .source(runtimeSource)
                .status("SUCCEEDED")
                .summary(sourceType.trim() + " authoritative datasource query succeeded")
                .timestamp(persisted.observedAt())
                .payload(payload)
                .build();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
