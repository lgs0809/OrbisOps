package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetGenerationInput;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsExecutionTargetGenerationMapper {

    public ExecutionTargetGenerationInput input(
            String projectId,
            String templateId,
            Map<String, Object> request) {
        Map<String, Object> command = request == null ? Map.of() : request;
        return new ExecutionTargetGenerationInput(
                projectId,
                templateId,
                firstText(command.get("executionTargetId"), command.get("resourceId")),
                firstText(command.get("targetName"), command.get("name")),
                text(command.get("workerId")),
                list(command.get("environments")),
                map(command.get("configuration")),
                map(command.get("resolvedConfig")),
                list(command.get("allowedActions")),
                command.containsKey("approvalRequired")
                        ? bool(command.get("approvalRequired"))
                        : null,
                ExecutionResourceStatus.require(text(command.get("status"))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object source) {
        if (source instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        String raw = text(source);
        if (raw.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.parseObject(raw, LinkedHashMap.class);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("EXECUTION_TARGET_CONFIG_INVALID", error);
        }
    }

    private List<String> list(Object source) {
        if (source instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object item : iterable) {
                add(result, item);
            }
            return List.copyOf(result);
        }
        String raw = text(source);
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(raw, String.class);
            return values == null ? List.of() : values.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of(raw);
        }
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank() && !target.contains(normalized)) {
            target.add(normalized);
        }
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(text(value));
    }

    private String firstText(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
