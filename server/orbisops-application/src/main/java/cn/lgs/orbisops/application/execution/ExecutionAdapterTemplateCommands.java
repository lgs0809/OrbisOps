package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExecutionAdapterTemplateCommands {

    private ExecutionAdapterTemplateCommands() {
    }

    public record Field<T>(boolean supplied, T value) {
        public static <T> Field<T> absent() {
            return new Field<>(false, null);
        }

        public static <T> Field<T> supplied(T value) {
            return new Field<>(true, value);
        }

        public T orElse(T fallback) {
            return supplied ? value : fallback;
        }
    }

    public record Mutation(
            Field<String> templateId,
            Field<String> templateName,
            Field<ExecutionAdapterType> adapterType,
            Field<List<String>> supportedActions,
            Field<Map<String, Object>> defaultConfig,
            Field<ExecutionRiskLevel> riskLevel,
            Field<Boolean> readOnly,
            Field<String> description,
            Field<ExecutionResourceStatus> status,
            String actor) {
        public Mutation {
            templateId = field(templateId);
            templateName = field(templateName);
            adapterType = field(adapterType);
            supportedActions = supportedActions == null || !supportedActions.supplied()
                    ? Field.absent()
                    : Field.supplied(supportedActions.value() == null ? List.of() : List.copyOf(supportedActions.value()));
            defaultConfig = defaultConfig == null || !defaultConfig.supplied()
                    ? Field.absent()
                    : Field.supplied(copy(defaultConfig.value()));
            riskLevel = field(riskLevel);
            readOnly = field(readOnly);
            description = field(description);
            status = field(status);
            actor = required(actor, "EXECUTION_TEMPLATE_ACTOR_REQUIRED");
        }
    }

    public record StatusChange(ExecutionResourceStatus status, String actor) {
        public StatusChange {
            if (status == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_STATUS_REQUIRED");
            actor = required(actor, "EXECUTION_TEMPLATE_ACTOR_REQUIRED");
        }
    }

    public record TargetGeneration(String projectId,
                                   String templateId,
                                   Map<String, Object> request,
                                   String actor) {
        public TargetGeneration {
            projectId = required(projectId, "EXECUTION_PROJECT_ID_REQUIRED");
            templateId = required(templateId, "EXECUTION_TEMPLATE_ID_REQUIRED");
            request = copy(request);
            actor = required(actor, "EXECUTION_TEMPLATE_ACTOR_REQUIRED");
        }
    }

    private static <T> Field<T> field(Field<T> value) {
        return value == null ? Field.absent() : value;
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
