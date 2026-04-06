package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCommands;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class OpsExecutionAdapterTemplateCommandMapper {

    public ExecutionAdapterTemplateCommands.Mutation mutation(Map<String, Object> request, String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ExecutionAdapterTemplateCommands.Mutation(
                aliasTextField(safe, "adapterTemplateId", "templateId"),
                aliasTextField(safe, "templateName", "name"),
                aliasEnumField(safe, ExecutionAdapterType::require, "adapterType", "adapter"),
                listField(safe, "supportedActions"),
                mapField(safe, "defaultConfig"),
                enumField(safe, "riskLevel", ExecutionRiskLevel::require),
                booleanField(safe, "readOnly"),
                textField(safe, "description"),
                enumField(safe, "status", ExecutionResourceStatus::require),
                actor);
    }

    public ExecutionAdapterTemplateCommands.StatusChange status(Map<String, Object> request, String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ExecutionAdapterTemplateCommands.StatusChange(
                ExecutionResourceStatus.require(text(safe.getOrDefault("status", "DISABLED"))), actor);
    }

    public ExecutionAdapterTemplateCommands.TargetGeneration target(String projectId,
                                                                     Map<String, Object> request,
                                                                     String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String templateId = firstText(safe.get("adapterTemplateId"), safe.get("templateId"));
        return new ExecutionAdapterTemplateCommands.TargetGeneration(projectId, templateId, safe, actor);
    }

    private ExecutionAdapterTemplateCommands.Field<String> textField(Map<String, Object> source, String key) {
        return source.containsKey(key)
                ? ExecutionAdapterTemplateCommands.Field.supplied(text(source.get(key)))
                : ExecutionAdapterTemplateCommands.Field.absent();
    }

    private ExecutionAdapterTemplateCommands.Field<String> aliasTextField(Map<String, Object> source,
                                                                           String primary,
                                                                           String alias) {
        if (source.containsKey(primary)) return textField(source, primary);
        return textField(source, alias);
    }

    private <T> ExecutionAdapterTemplateCommands.Field<T> enumField(
            Map<String, Object> source,
            String key,
            java.util.function.Function<String, T> parser) {
        return source.containsKey(key)
                ? ExecutionAdapterTemplateCommands.Field.supplied(parser.apply(text(source.get(key))))
                : ExecutionAdapterTemplateCommands.Field.absent();
    }

    private <T> ExecutionAdapterTemplateCommands.Field<T> aliasEnumField(
            Map<String, Object> source,
            java.util.function.Function<String, T> parser,
            String primary,
            String alias) {
        if (source.containsKey(primary)) return enumField(source, primary, parser);
        return enumField(source, alias, parser);
    }

    private ExecutionAdapterTemplateCommands.Field<Boolean> booleanField(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) return ExecutionAdapterTemplateCommands.Field.absent();
        Object value = source.get(key);
        boolean result = value instanceof Boolean bool
                ? bool
                : value instanceof Number number
                ? number.intValue() != 0
                : "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
        return ExecutionAdapterTemplateCommands.Field.supplied(result);
    }

    private ExecutionAdapterTemplateCommands.Field<List<String>> listField(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) return ExecutionAdapterTemplateCommands.Field.absent();
        Object value = source.get(key);
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            for (String item : text(value).split("[,，\\n]")) add(result, item);
        }
        return ExecutionAdapterTemplateCommands.Field.supplied(result.stream().distinct().toList());
    }

    private ExecutionAdapterTemplateCommands.Field<Map<String, Object>> mapField(Map<String, Object> source,
                                                                                  String key) {
        if (!source.containsKey(key)) return ExecutionAdapterTemplateCommands.Field.absent();
        Map<String, Object> result = new LinkedHashMap<>();
        if (source.get(key) instanceof Map<?, ?> map) {
            map.forEach((itemKey, itemValue) -> result.put(String.valueOf(itemKey), itemValue));
        }
        return ExecutionAdapterTemplateCommands.Field.supplied(Map.copyOf(result));
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
