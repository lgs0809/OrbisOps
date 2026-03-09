package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolOutputBudget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persists datasource observations through the existing ToolResult + Evidence stores. */
@Service
class OpsAuthoritativeDatasourceEvidenceStore {

    private static final SensitiveDataRedactionPolicy REDACTION = new SensitiveDataRedactionPolicy();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DEPTH = 8;
    private static final int MAX_MAP_ENTRIES = 64;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_TEXT_CHARS = 1200;

    private final OpsToolResultStore toolResults;
    private final OpsEvidenceStore evidence;

    OpsAuthoritativeDatasourceEvidenceStore(OpsToolResultStore toolResults,
                                            OpsEvidenceStore evidence) {
        this.toolResults = toolResults;
        this.evidence = evidence;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    PersistedEvidence persist(OpsAgentRunRequestDTO request,
                              String sourceType,
                              String source,
                              Map<String, Object> structuredSummary) {
        if (request == null) throw new IllegalArgumentException("DATASOURCE_EVIDENCE_REQUEST_REQUIRED");
        String projectId = required(request.getProjectId(), "DATASOURCE_EVIDENCE_PROJECT_REQUIRED");
        String runId = required(request.getRunId(), "DATASOURCE_EVIDENCE_RUN_REQUIRED");
        String normalizedSourceType = required(sourceType, "DATASOURCE_EVIDENCE_SOURCE_TYPE_REQUIRED").toUpperCase(Locale.ROOT);
        String normalizedSource = required(source, "DATASOURCE_EVIDENCE_SOURCE_REQUIRED");
        String actor = text(request.getRequestedBy()).isBlank() ? "ops-runtime" : text(request.getRequestedBy());

        Object canonical = CanonicalJson.parse(CanonicalJson.stringify(
                REDACTION.redact(structuredSummary == null ? Map.of() : structuredSummary)));
        Object bounded = bound(canonical, 0);
        String output = CanonicalJson.stringify(bounded);
        String expectedOutputHash = CanonicalObjectHasher.sha256(bounded);
        String toolName = normalizedSourceType.toLowerCase(Locale.ROOT) + "_query_protocol";

        Map<String, Object> stored = toolResults.record(
                projectId,
                "",
                runId,
                actor,
                "datasource_query",
                toolName,
                "AUTHORITATIVE_DATASOURCE:" + normalizedSourceType,
                normalizedSource,
                output,
                OpsToolOutputBudget.builder().build(),
                actor,
                "SUCCEEDED",
                0L);
        String resultId = required(stored.get("resultId"), "DATASOURCE_TOOL_RESULT_ID_REQUIRED");
        String outputHash = required(stored.get("outputHash"), "DATASOURCE_TOOL_RESULT_HASH_REQUIRED").toLowerCase(Locale.ROOT);
        String fullOutputRef = required(stored.get("fullOutputRef"), "DATASOURCE_TOOL_RESULT_REF_REQUIRED");
        if (!expectedOutputHash.equals(outputHash)) {
            throw new IllegalStateException("DATASOURCE_TOOL_RESULT_HASH_MISMATCH");
        }

        Map<String, Object> proof = evidence.record(
                projectId,
                runId,
                normalizedSourceType,
                normalizedSource,
                resultId,
                outputHash,
                fullOutputRef,
                text(stored.get("preview")),
                true,
                Map.of(
                        "source", normalizedSource,
                        "toolsetId", "datasource_query",
                        "toolName", toolName,
                        "authoritativeDatasource", true),
                actor);
        String evidenceId = required(proof.get("evidenceId"), "DATASOURCE_EVIDENCE_ID_REQUIRED");
        if (!Boolean.TRUE.equals(proof.get("verified"))) {
            throw new IllegalStateException("DATASOURCE_EVIDENCE_NOT_VERIFIED");
        }
        return new PersistedEvidence(
                resultId,
                evidenceId,
                outputHash,
                fullOutputRef,
                bounded,
                LocalDateTime.now().format(TIMESTAMP));
    }

    private Object bound(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof String text) return abbreviate(text);
        if (depth >= MAX_DEPTH) return abbreviate(CanonicalJson.stringify(value));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_MAP_ENTRIES) break;
                result.put(String.valueOf(entry.getKey()), bound(entry.getValue(), depth + 1));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            int limit = Math.min(list.size(), MAX_LIST_ITEMS);
            for (int index = 0; index < limit; index++) result.add(bound(list.get(index), depth + 1));
            return result;
        }
        return bound(CanonicalJson.parse(CanonicalJson.stringify(value)), depth + 1);
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= MAX_TEXT_CHARS) return value;
        return value.substring(0, MAX_TEXT_CHARS);
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalStateException(error);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record PersistedEvidence(String resultId,
                             String evidenceId,
                             String outputHash,
                             String fullOutputRef,
                             Object structuredSummary,
                             String observedAt) {
    }
}
