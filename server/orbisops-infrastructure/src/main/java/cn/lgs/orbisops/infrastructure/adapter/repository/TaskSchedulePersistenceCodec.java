package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.application.schedule.TaskScheduleScreeningConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Jackson ACL for the persisted scheduled-task runtime payload. */
final class TaskSchedulePersistenceCodec {

    private final ObjectMapper objectMapper;

    TaskSchedulePersistenceCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_OBJECT_MAPPER_REQUIRED");
        }
        this.objectMapper = objectMapper;
    }

    String encode(TaskScheduleRuntimeConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CONFIGURATION_REQUIRED");
        }
        ObjectNode value = objectMapper.createObjectNode();
        put(value, "projectId", configuration.projectId());
        put(value, "executionType", configuration.executionType());
        put(value, "agentBindingMode", configuration.agentBindingMode());
        put(value, "agentVersion", configuration.agentVersion());
        put(value, "agentDefinitionHash", configuration.agentDefinitionHash());
        put(value, "prompt", configuration.prompt());
        put(value, "rangeMinutes", configuration.rangeMinutes());
        put(value, "promWindow", configuration.promWindow());
        put(value, "includeRecentLogs", configuration.includeRecentLogs());
        put(value, "maxRounds", configuration.maxRounds());
        put(value, "subAgentMaxIterations", configuration.subAgentMaxIterations());
        put(value, "nodeTimeoutSeconds", configuration.nodeTimeoutSeconds());
        put(value, "maxEvidenceItems", configuration.maxEvidenceItems());
        put(value, "notifyChannel", configuration.notifyChannel());
        put(value, "notificationChannelId", configuration.notificationChannelId());
        put(value, "notificationTarget", configuration.notificationTarget());

        TaskScheduleScreeningConfiguration screening = configuration.screening();
        ObjectNode screeningNode = objectMapper.createObjectNode();
        screeningNode.put("enabled", screening.enabled());
        put(screeningNode, "sourceType", screening.sourceType());
        put(screeningNode, "primaryUri", screening.primaryUri());
        put(screeningNode, "maxErrorRatePercent", screening.maxErrorRatePercent());
        put(screeningNode, "maxCpuPercent", screening.maxCpuPercent());
        put(screeningNode, "maxHeapPercent", screening.maxHeapPercent());
        put(screeningNode, "minInstanceUpRatio", screening.minInstanceUpRatio());
        value.set("screening", screeningNode);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("周期任务配置 JSON 不合法", error);
        }
    }

    TaskScheduleRuntimeConfiguration decode(String taskParam) {
        if (taskParam == null || taskParam.trim().isBlank()) {
            throw new IllegalStateException("周期任务缺少项目级 Agent 运行配置，请重新保存任务");
        }
        String value = taskParam.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) {
            throw new IllegalStateException("周期任务仍使用旧版文本配置，请重新创建任务");
        }
        try {
            JsonNode config = objectMapper.readTree(value);
            JsonNode screening = config.path("screening");
            TaskScheduleScreeningConfiguration screeningConfiguration = screening.isMissingNode() || screening.isNull()
                    ? TaskScheduleScreeningConfiguration.disabled()
                    : new TaskScheduleScreeningConfiguration(
                    booleanValue(screening, "enabled", false),
                    textValue(screening, "sourceType"),
                    textValue(screening, "primaryUri"),
                    doubleValue(screening, "maxErrorRatePercent"),
                    doubleValue(screening, "maxCpuPercent"),
                    doubleValue(screening, "maxHeapPercent"),
                    doubleValue(screening, "minInstanceUpRatio"));
            return new TaskScheduleRuntimeConfiguration(
                    textValue(config, "projectId"),
                    textValue(config, "executionType"),
                    textValue(config, "agentBindingMode"),
                    integerValue(config, "agentVersion"),
                    textValue(config, "agentDefinitionHash"),
                    textValue(config, "prompt"),
                    integerValue(config, "rangeMinutes"),
                    textValue(config, "promWindow"),
                    booleanValue(config, "includeRecentLogs"),
                    integerValue(config, "maxRounds"),
                    integerValue(config, "subAgentMaxIterations"),
                    integerValue(config, "nodeTimeoutSeconds"),
                    integerValue(config, "maxEvidenceItems"),
                    booleanValue(config, "notifyChannel"),
                    textValue(config, "notificationChannelId"),
                    textValue(config, "notificationTarget"),
                    screeningConfiguration);
        } catch (Exception error) {
            if (error instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("周期任务配置 JSON 不合法", error);
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private boolean booleanValue(JsonNode node, String field, boolean fallback) {
        Boolean value = booleanValue(node, field);
        return value == null ? fallback : value;
    }

    private Double doubleValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private void put(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void put(ObjectNode node, String field, Integer value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void put(ObjectNode node, String field, Boolean value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void put(ObjectNode node, String field, Double value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
