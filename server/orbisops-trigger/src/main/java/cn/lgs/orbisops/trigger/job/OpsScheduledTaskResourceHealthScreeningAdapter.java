package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.resourcehealth.ResourceHealthApplicationService;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthSnapshot;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.ScheduledTaskScreeningPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskScreeningResult;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec;
import cn.lgs.orbisops.trigger.ops.OpsScheduledPrometheusScreeningService;

import java.util.List;
import java.util.Set;

/** Task-level read-only screening adapter. Endpoint health alone is never treated as NORMAL. */
public final class OpsScheduledTaskResourceHealthScreeningAdapter implements ScheduledTaskScreeningPort {

    private static final Set<String> EVIDENCE_SOURCE_IDS = Set.of(
            "mysql_slow_sql", "elasticsearch", "prometheus");

    private final ResourceHealthApplicationService resourceHealth;
    private final OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec;
    private final OpsScheduledPrometheusScreeningService prometheusScreening;

    /** Legacy/fallback constructor: keeps fail-safe behavior and never emits NORMAL from endpoint health. */
    public OpsScheduledTaskResourceHealthScreeningAdapter(ResourceHealthApplicationService resourceHealth) {
        if (resourceHealth == null) throw new IllegalArgumentException("RESOURCE_HEALTH_SERVICE_REQUIRED");
        this.resourceHealth = resourceHealth;
        this.runtimeCodec = null;
        this.prometheusScreening = null;
    }

    public OpsScheduledTaskResourceHealthScreeningAdapter(
            ResourceHealthApplicationService resourceHealth,
            OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec,
            OpsScheduledPrometheusScreeningService prometheusScreening) {
        if (resourceHealth == null) throw new IllegalArgumentException("RESOURCE_HEALTH_SERVICE_REQUIRED");
        if (runtimeCodec == null) throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CODEC_REQUIRED");
        if (prometheusScreening == null) throw new IllegalArgumentException("SCHEDULE_PROMETHEUS_SCREENING_REQUIRED");
        this.resourceHealth = resourceHealth;
        this.runtimeCodec = runtimeCodec;
        this.prometheusScreening = prometheusScreening;
    }

    @Override
    public ScheduledTaskScreeningResult screen(ScheduledTaskExecutionCommand command) {
        if (runtimeCodec != null && prometheusScreening != null) {
            TaskScheduleRuntimeConfiguration runtime;
            try {
                runtime = runtimeCodec.decode(command.runtimePayload());
            } catch (RuntimeException error) {
                return ScheduledTaskScreeningResult.configError(
                        "巡检筛查配置不可读：" + safeMessage(error));
            }
            return prometheusScreening.screen(runtime);
        }
        return endpointHealthFallback();
    }

    private ScheduledTaskScreeningResult endpointHealthFallback() {
        ResourceHealthSnapshot snapshot = resourceHealth.snapshot();
        List<ResourceHealthCheck> configured = snapshot.checks().stream()
                .filter(check -> EVIDENCE_SOURCE_IDS.contains(check.id()))
                .filter(check -> check.endpoint() != null && !check.endpoint().isBlank())
                .toList();
        if (configured.isEmpty()) {
            return ScheduledTaskScreeningResult.deepRequired(
                    "没有可用于轻量巡检的已配置指标/日志/数据库探针，转入深度调查");
        }
        List<ResourceHealthCheck> abnormal = configured.stream()
                .filter(check -> !check.healthy())
                .toList();
        if (abnormal.isEmpty()) {
            return ScheduledTaskScreeningResult.deepRequired(
                    "数据源探针可用，但未配置任务级业务判定规则，不能据此判定业务正常，转入深度调查");
        }
        return new ScheduledTaskScreeningResult(
                ScheduledTaskScreeningResult.Status.INCONCLUSIVE,
                "数据源可用性检查异常，业务状态无法可靠判定：" + abnormal.stream()
                        .map(check -> check.name() + "(" + check.message() + ")")
                        .toList(),
                abnormal.stream().map(ResourceHealthCheck::id).toList());
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        }
        return error.getMessage().trim();
    }
}
