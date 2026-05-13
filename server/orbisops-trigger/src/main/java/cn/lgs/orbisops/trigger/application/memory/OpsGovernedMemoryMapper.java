package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.CaptureMemoryResult;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreateCommand;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreationResult;
import cn.lgs.orbisops.application.memory.GovernedMemoryVerifyCommand;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anti-corruption mapper between legacy governed-memory Maps and typed application contracts. */
@Component
public class OpsGovernedMemoryMapper {

    public GovernedMemoryCreateCommand createCommand(
            Map<String, Object> request,
            String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new GovernedMemoryCreateCommand(
                required(safe.get("scopeType"), "scopeType 不能为空"),
                required(safe.get("scopeId"), "scopeId 不能为空"),
                required(safe.get("memoryType"), "memoryType 不能为空"),
                requiredContent(safe.get("content")),
                text(safe.get("normalizedContent")),
                text(safe.get("logicalKey")),
                text(safe.get("userId")),
                text(safe.get("projectId")),
                text(safe.get("agentId")),
                text(safe.get("sessionId")),
                text(safe.get("sourceType")),
                text(safe.get("sourceRunId")),
                bool(safe.get("verified")),
                decimal(safe.get("confidence"), 0.6D),
                text(safe.get("riskLevel")),
                proofRefs(safe.get("proofRefs")),
                actor);
    }

    public GovernedMemoryVerifyCommand verifyCommand(
            String memoryId,
            List<Map<String, Object>> proofRefs,
            String actor) {
        return new GovernedMemoryVerifyCommand(memoryId, proofRefs, actor);
    }

    public GovernedMemoryRuntimeQuery runtimeQuery(
            String userId,
            String projectId,
            String sessionId,
            String excludedSourceRunId,
            int limit) {
        return new GovernedMemoryRuntimeQuery(
                userId,
                projectId,
                sessionId,
                excludedSourceRunId,
                limit);
    }

    public Map<String, Object> captureView(CaptureMemoryResult result) {
        return result == null ? Map.of() : creationView(result.memory());
    }

    public Map<String, Object> creationView(GovernedMemoryCreationResult result) {
        if (result == null) return Map.of();
        Map<String, Object> data = view(result.snapshot());
        if (!result.conflictId().isBlank()) data.put("conflictId", result.conflictId());
        return data;
    }

    public List<Map<String, Object>> views(List<GovernedMemorySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();
        return snapshots.stream().filter(snapshot -> snapshot != null).map(this::view).toList();
    }

    public Map<String, Object> view(GovernedMemorySnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", snapshot.databaseId());
        data.put("memoryId", snapshot.memoryId());
        data.put("scopeType", snapshot.scope().name());
        data.put("scopeId", snapshot.scopeId());
        data.put("userId", snapshot.userId());
        data.put("projectId", snapshot.projectId());
        data.put("agentId", snapshot.agentId());
        data.put("sessionId", snapshot.sessionId());
        data.put("memoryType", snapshot.type().name());
        data.put("logicalKey", snapshot.logicalKey());
        data.put("content", snapshot.content());
        data.put("normalizedContent", snapshot.normalizedContent());
        data.put("sourceType", snapshot.sourceType());
        data.put("sourceRunId", snapshot.sourceRunId());
        data.put("verified", snapshot.verified() ? 1 : 0);
        data.put("confidence", snapshot.confidence());
        data.put("riskLevel", snapshot.riskLevel());
        data.put("status", snapshot.status());
        data.put("version", snapshot.version());
        data.put("memoryHash", snapshot.memoryHash());
        data.put("proofRefsJson", JSON.toJSONString(snapshot.proofRefs()));
        data.put("expiresAt", instant(snapshot.expiresAt()));
        data.put("createdBy", snapshot.createdBy());
        data.put("idempotencyKey", snapshot.idempotencyKey());
        data.put("createTime", instant(snapshot.createdAt()));
        data.put("updateTime", instant(snapshot.updatedAt()));
        return data;
    }

    private List<Map<String, Object>> proofRefs(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, current) -> copy.put(String.valueOf(key), current));
            result.add(copy);
        }
        return result;
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String requiredContent(Object value) {
        String content = value == null ? "" : String.valueOf(value);
        if (content.trim().isBlank()) throw new IllegalArgumentException("Memory content 不能为空");
        return content;
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private double decimal(Object value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
