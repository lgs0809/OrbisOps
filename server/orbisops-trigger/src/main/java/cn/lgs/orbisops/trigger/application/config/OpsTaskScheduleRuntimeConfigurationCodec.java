package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.application.schedule.TaskScheduleScreeningConfiguration;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

/** Fastjson persistence protocol adapter for scheduled Agent runtime configuration. */
public final class OpsTaskScheduleRuntimeConfigurationCodec {

    public String encode(TaskScheduleRuntimeConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CONFIGURATION_REQUIRED");
        }
        JSONObject value = new JSONObject(true);
        value.put("projectId", configuration.projectId());
        value.put("executionType", configuration.executionType());
        value.put("agentBindingMode", configuration.agentBindingMode());
        value.put("agentVersion", configuration.agentVersion());
        value.put("agentDefinitionHash", configuration.agentDefinitionHash());
        value.put("prompt", configuration.prompt());
        value.put("rangeMinutes", configuration.rangeMinutes());
        value.put("promWindow", configuration.promWindow());
        value.put("includeRecentLogs", configuration.includeRecentLogs());
        value.put("maxRounds", configuration.maxRounds());
        value.put("subAgentMaxIterations", configuration.subAgentMaxIterations());
        value.put("nodeTimeoutSeconds", configuration.nodeTimeoutSeconds());
        value.put("maxEvidenceItems", configuration.maxEvidenceItems());
        value.put("notifyChannel", configuration.notifyChannel());
        value.put("notificationChannelId", configuration.notificationChannelId());
        value.put("notificationTarget", configuration.notificationTarget());
        JSONObject screening = new JSONObject(true);
        screening.put("enabled", configuration.screening().enabled());
        screening.put("sourceType", configuration.screening().sourceType());
        screening.put("primaryUri", configuration.screening().primaryUri());
        screening.put("maxErrorRatePercent", configuration.screening().maxErrorRatePercent());
        screening.put("maxCpuPercent", configuration.screening().maxCpuPercent());
        screening.put("maxHeapPercent", configuration.screening().maxHeapPercent());
        screening.put("minInstanceUpRatio", configuration.screening().minInstanceUpRatio());
        value.put("screening", screening);
        return value.toJSONString();
    }

    public TaskScheduleRuntimeConfiguration decode(String taskParam) {
        if (!StringUtils.hasText(taskParam)) {
            throw new IllegalStateException("周期任务缺少项目级 Agent 运行配置，请重新保存任务");
        }
        String value = taskParam.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) {
            throw new IllegalStateException("周期任务仍使用旧版文本配置，请重新创建任务");
        }
        try {
            JSONObject config = JSONObject.parseObject(value);
            JSONObject screening = config.getJSONObject("screening");
            TaskScheduleScreeningConfiguration screeningConfiguration = screening == null
                    ? TaskScheduleScreeningConfiguration.disabled()
                    : new TaskScheduleScreeningConfiguration(
                    Boolean.TRUE.equals(screening.getBoolean("enabled")),
                    screening.getString("sourceType"),
                    screening.getString("primaryUri"),
                    screening.getDouble("maxErrorRatePercent"),
                    screening.getDouble("maxCpuPercent"),
                    screening.getDouble("maxHeapPercent"),
                    screening.getDouble("minInstanceUpRatio"));
            return new TaskScheduleRuntimeConfiguration(
                    config.getString("projectId"),
                    config.getString("executionType"),
                    config.getString("agentBindingMode"),
                    config.getInteger("agentVersion"),
                    config.getString("agentDefinitionHash"),
                    config.getString("prompt"),
                    config.getInteger("rangeMinutes"),
                    config.getString("promWindow"),
                    config.getBoolean("includeRecentLogs"),
                    config.getInteger("maxRounds"),
                    config.getInteger("subAgentMaxIterations"),
                    config.getInteger("nodeTimeoutSeconds"),
                    config.getInteger("maxEvidenceItems"),
                    config.getBoolean("notifyChannel"),
                    config.getString("notificationChannelId"),
                    config.getString("notificationTarget"),
                    screeningConfiguration);
        } catch (Exception error) {
            throw new IllegalStateException("周期任务配置 JSON 不合法", error);
        }
    }
}
