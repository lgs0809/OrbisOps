package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.memory.CaptureMemoryCommand;
import cn.lgs.orbisops.application.memory.CaptureMemoryResult;
import cn.lgs.orbisops.application.memory.CaptureMemoryUseCase;
import cn.lgs.orbisops.application.memory.QueryRuntimeMemoryUseCase;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic explicit-Memory tool boundary. Implicit learning is intentionally excluded. */
@Component
public final class OpsMemoryToolExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ObjectProvider<CaptureMemoryUseCase> capture;
    private final ObjectProvider<QueryRuntimeMemoryUseCase> query;

    public OpsMemoryToolExecutionDispatchHandler(
            ObjectProvider<CaptureMemoryUseCase> capture,
            ObjectProvider<QueryRuntimeMemoryUseCase> query) {
        this.capture = capture;
        this.query = query;
    }

    @Override
    public String handlerId() {
        return "memory";
    }

    @Override
    public int order() {
        return 180;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "memory".equals(target.toolsetId())
                || "MEMORY".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        return switch (target.toolName()) {
            case "memory_upsert" -> upsert(request);
            case "memory_search" -> search(request);
            default -> throw new IllegalArgumentException("未知 Memory 工具：" + target.toolName());
        };
    }

    private Map<String, Object> upsert(ToolExecutionRequest request) {
        CaptureMemoryUseCase useCase = available(capture, "Memory capture use case 未初始化");
        String content = required(
                firstText(request.arguments().get("content"), request.arguments().get("memory")),
                "MEMORY_CONTENT_REQUIRED");
        String userId = firstText(request.userId(), request.actor());
        CaptureMemoryResult result = useCase.capture(new CaptureMemoryCommand(
                content,
                userId,
                request.projectId(),
                text(request.requestContext().get("agentId")),
                request.sessionId(),
                request.runId(),
                "LOW"));
        GovernedMemorySnapshot snapshot = result.memory().snapshot();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "COMMITTED");
        response.put("memoryId", snapshot.memoryId());
        response.put("memoryType", snapshot.type().name());
        response.put("scope", snapshot.scope().name());
        response.put("scopeId", snapshot.scopeId());
        response.put("version", snapshot.version());
        response.put("duplicate", result.memory().duplicate());
        response.put("conflictId", result.memory().conflictId());
        response.put("visibleToCurrentRunByUserMessage", true);
        response.put("automaticallyReinjectCurrentRun", false);
        return Map.copyOf(response);
    }

    private Map<String, Object> search(ToolExecutionRequest request) {
        QueryRuntimeMemoryUseCase useCase = available(query, "Memory query use case 未初始化");
        int limit = intValue(request.arguments().get("limit"), 20);
        String userId = firstText(request.userId(), request.actor());
        List<GovernedMemorySnapshot> items = useCase.select(
                userId,
                request.projectId(),
                request.sessionId(),
                "",
                limit);
        return Map.of("items", items, "count", items.size());
    }

    private <T> T available(ObjectProvider<T> provider, String message) {
        T value = provider == null ? null : provider.getIfAvailable();
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private String required(String value, String reasonCode) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(reasonCode);
        return value.trim();
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            int parsed = value == null ? fallback : Integer.parseInt(String.valueOf(value));
            return Math.max(1, Math.min(parsed, 100));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
