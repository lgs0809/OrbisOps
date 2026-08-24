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
            "PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL", "RABBITMQ", "REDIS",
            "SERVICE_CONTROL");
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
            String toolKind = text(payload.get("toolKind"));
            String authoritativeMcpType = authoritativeMcpSourceType(payload);
            String remoteToolName = remoteToolName(payload);
            sourceType = !authoritativeMcpType.isBlank()
                    ? authoritativeMcpType
                    : "mcp".equalsIgnoreCase(toolKind) ? "MCP" : "TOOL";
            String resultId = text(payload.get("resultId"));
            sourceId = !resultId.isBlank() ? resultId : runId + ":" + index + ":" + toolName;
            summary = text(event.getSummary()) + "\n" + abbreviate(output);
            metadata.put("toolName", toolName);
            metadata.put("nodeId", text(event.getNodeId()));
            metadata.put("owner", text(payload.get("owner")));
            metadata.put("durationMs", payload.getOrDefault("durationMs", 0));
            if (!authoritativeMcpType.isBlank()) {
                metadata.put("verified", true);
                metadata.put("resultId", resultId);
                metadata.put("mcpId", text(payload.get("mcpId")));
                metadata.put("remoteToolName", remoteToolName);
            }
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

    private String authoritativeMcpSourceType(Map<String, Object> payload) {
        if (payload == null
                || !"mcp".equalsIgnoreCase(text(payload.get("toolKind")))
                || !Boolean.TRUE.equals(payload.get("remoteCallExecuted"))
                || Boolean.FALSE.equals(payload.get("allowed"))
                || text(payload.get("resultId")).isBlank()) {
            return "";
        }
        String name = remoteToolName(payload).toLowerCase();
        if (name.isBlank()) return "";
        String mcpId = text(payload.get("mcpId")).toLowerCase();
        if (mcpId.contains("service-control") || mcpId.contains("service_control")
                || "get_service_status".equals(name)
                || "restart_service_dry_run".equals(name)
                || "restart_service".equals(name)
                || "get_operation_receipt".equals(name)) {
            return "SERVICE_CONTROL";
        }
        if (name.startsWith("rabbitmq_") || mcpId.contains("rabbitmq")) return "RABBITMQ";
        if (name.startsWith("redis_") || mcpId.contains("redis")) return "REDIS";
        if (name.startsWith("prometheus_") || mcpId.contains("prometheus")) return "PROMETHEUS";
        if (name.startsWith("elasticsearch_") || name.startsWith("es_")
                || mcpId.contains("elasticsearch") || mcpId.contains("-es-")) return "ELASTICSEARCH";
        if ((name.contains("mysql") || mcpId.contains("mysql"))
                && (name.contains("slow") || name.contains("digest")
                || mcpId.contains("slow") || mcpId.contains("digest"))) {
            return "MYSQL_SLOW_SQL";
        }
        if (name.startsWith("mysql_") || mcpId.contains("mysql")) return "MYSQL";
        return "";
    }

    /**
     * Some MCP adapters put the authoritative remote name on the event, while
     * others only include it in the provider JSON returned by the callback.
     * Normalize both shapes before applying evidence policy.
     */
    @SuppressWarnings("unchecked")
    private String remoteToolName(Map<String, Object> payload) {
        if (payload == null) return "";
        String name = text(payload.get("remoteToolName"));
        if (!name.isBlank()) return name;
        Object parsed = null;
        String output = text(payload.get("output"));
        if (!output.isBlank()) {
            try {
                parsed = JSON.parseObject(output, Map.class);
            } catch (RuntimeException ignored) {
                // The output is still useful evidence; an unparsable preview
                // simply means the event has no normalized remote name.
            }
        }
        if (parsed instanceof Map<?, ?> map) {
            name = text(map.get("remoteToolName"));
            if (!name.isBlank()) return name;
            name = text(map.get("toolName"));
        }
        return name;
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
