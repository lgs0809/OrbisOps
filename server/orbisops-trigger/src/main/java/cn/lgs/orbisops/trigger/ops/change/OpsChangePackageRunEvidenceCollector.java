package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import com.alibaba.fastjson.JSON;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects successful Runtime events into bounded ChangePackage evidence references. */
final class OpsChangePackageRunEvidenceCollector {

    private static final String PREPARE_TOOL_NAME = "PrepareChangePackage";
    private static final String SOURCE_QUERY_FINISHED = "SOURCE_QUERY_FINISHED";
    private static final Set<String> AUTHORITATIVE_DATASOURCE_TYPES = Set.of(
            "PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL");
    private static final int MAX_EVIDENCE = 20;
    private static final int MAX_SUMMARY_CHARS = 1800;

    private final Clock clock;

    OpsChangePackageRunEvidenceCollector() {
        this(Clock.systemDefaultZone());
    }

    OpsChangePackageRunEvidenceCollector(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    List<Evidence> collect(String runId, List<OpsRuntimeEvent> events) {
        if (text(runId).isBlank() || events == null || events.isEmpty()) return List.of();
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        List<Evidence> result = new ArrayList<>();
        for (OpsRuntimeEvent event : snapshot) {
            if (result.size() >= MAX_EVIDENCE) break;
            Evidence evidence = from(runId, event, result.size());
            if (evidence != null) result.add(evidence);
        }
        return List.copyOf(result);
    }

    private Evidence from(String runId, OpsRuntimeEvent event, int index) {
        if (event == null || !"SUCCEEDED".equalsIgnoreCase(text(event.getStatus()))) return null;
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        String sourceType;
        String sourceId;
        String summary;
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (SOURCE_QUERY_FINISHED.equals(event.getEventType())) {
            sourceType = text(payload.get("sourceType")).toUpperCase();
            String source = text(payload.get("source"));
            String resultId = text(payload.get("resultId"));
            String evidenceId = text(payload.get("evidenceId"));
            String outputHash = text(payload.get("outputHash")).toLowerCase();
            String fullOutputRef = text(payload.get("fullOutputRef"));
            Object structuredSummary = payload.get("structuredSummary");
            boolean verified = Boolean.TRUE.equals(payload.get("verified"));
            if (!verified
                    || !AUTHORITATIVE_DATASOURCE_TYPES.contains(sourceType)
                    || source.isBlank()
                    || resultId.isBlank()
                    || evidenceId.isBlank()
                    || fullOutputRef.isBlank()
                    || !outputHash.matches("[0-9a-f]{64}")
                    || structuredSummary == null
                    || !outputHash.equals(CanonicalObjectHasher.sha256(structuredSummary))) {
                return null;
            }
            sourceId = resultId;
            summary = abbreviate(JSON.toJSONString(structuredSummary));
            metadata.put("verified", true);
            metadata.put("source", source);
            metadata.put("resultId", resultId);
            metadata.put("evidenceId", evidenceId);
            metadata.put("outputHash", outputHash);
            metadata.put("fullOutputRef", fullOutputRef);
            metadata.put("nodeId", text(event.getNodeId()));
            String observedAt = text(payload.get("observedAt"));
            if (observedAt.isBlank()) observedAt = text(event.getTimestamp());
            if (observedAt.isBlank()) observedAt = LocalDateTime.now(clock).toString();
            return new Evidence(
                    "run-evidence-" + (index + 1),
                    sourceType,
                    sourceId,
                    summary,
                    observedAt,
                    "sha256:" + outputHash,
                    metadata);
        } else if ("TOOL_CALL_FINISHED".equals(event.getEventType())) {
            String toolName = text(payload.get("toolName"));
            if (PREPARE_TOOL_NAME.equals(toolName) || "Skill".equals(toolName)) return null;
            String output = text(payload.get("output"));
            if (output.isBlank()) return null;
            sourceType = "mcp".equalsIgnoreCase(text(payload.get("toolKind"))) ? "MCP" : "TOOL";
            sourceId = runId + ":" + index + ":" + toolName;
            summary = text(event.getSummary()) + "\n" + abbreviate(output);
            metadata.put("toolName", toolName);
            metadata.put("nodeId", text(event.getNodeId()));
            metadata.put("owner", text(payload.get("owner")));
            metadata.put("durationMs", payload.getOrDefault("durationMs", 0));
        } else if ("RAG_RETRIEVE".equals(event.getEventType())) {
            sourceType = "RAG";
            sourceId = runId + ":rag:" + index;
            summary = text(event.getSummary()) + "\n" + abbreviate(JSON.toJSONString(payload));
            metadata.put("nodeId", text(event.getNodeId()));
        } else {
            return null;
        }
        String observedAt = text(event.getTimestamp());
        if (observedAt.isBlank()) observedAt = LocalDateTime.now(clock).toString();
        return new Evidence(
                "run-evidence-" + (index + 1),
                sourceType,
                sourceId,
                summary,
                observedAt,
                "sha256:" + sha256(summary),
                metadata);
    }

    private String abbreviate(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= MAX_SUMMARY_CHARS
                ? normalized
                : normalized.substring(0, MAX_SUMMARY_CHARS);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("证据摘要计算失败", e);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Evidence(String evidenceId,
                    String sourceType,
                    String sourceId,
                    String summary,
                    String observedAt,
                    String contentHash,
                    Map<String, Object> metadata) {
        Evidence {
            evidenceId = textValue(evidenceId);
            sourceType = textValue(sourceType);
            sourceId = textValue(sourceId);
            summary = textValue(summary);
            observedAt = textValue(observedAt);
            contentHash = textValue(contentHash);
            metadata = metadata == null || metadata.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("evidenceId", evidenceId);
            result.put("sourceType", sourceType);
            result.put("sourceId", sourceId);
            result.put("summary", summary);
            result.put("observedAt", observedAt);
            result.put("contentHash", contentHash);
            result.put("metadata", metadata);
            return Collections.unmodifiableMap(result);
        }

        private static String textValue(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
