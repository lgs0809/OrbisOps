package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility projection for typed execution-adapter template facts. */
public final class ExecutionAdapterTemplateView {

    private ExecutionAdapterTemplateView() {
    }

    public static Map<String, Object> of(
            ExecutionAdapterTemplate template,
            List<Map<String, Object>> generatedTargets) {
        if (template == null) throw new IllegalArgumentException("EXECUTION_ADAPTER_TEMPLATE_REQUIRED");
        List<Map<String, Object>> targets = generatedTargets == null ? List.of() : List.copyOf(generatedTargets);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.catalogId());
        result.put("adapterTemplateId", template.templateId());
        result.put("templateId", template.templateId());
        result.put("templateName", template.templateName());
        result.put("name", template.templateName());
        result.put("adapterType", template.adapterType().code());
        result.put("resourceType", template.adapterType().code());
        result.put("supportedActions", template.supportedActions());
        result.put("defaultConfig", template.defaultConfig());
        result.put("riskLevel", template.riskLevel().name());
        result.put("readOnly", template.readOnly());
        result.put("description", template.description());
        result.put("status", template.status().name());
        result.put("createBy", template.createBy());
        result.put("createTime", time(template.createdAt()));
        result.put("updateTime", time(template.updatedAt()));
        result.put("generatedTargetCount", targets.size());
        result.put("generatedTargets", targets);
        return Map.copyOf(result);
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
