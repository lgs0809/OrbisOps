package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.schedule.ScheduledTaskScreeningResult;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.application.schedule.TaskScheduleScreeningConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes the explicit Prometheus screening recipe persisted with a Schedule. */
public final class OpsScheduledPrometheusScreeningService {

    private final OpsPrometheusQueryProtocolService prometheus;
    private final OpsPrometheusSettings settings;

    public OpsScheduledPrometheusScreeningService(OpsPrometheusSettings settings) {
        this(new OpsPrometheusQueryProtocolService(), settings);
    }

    OpsScheduledPrometheusScreeningService(
            OpsPrometheusQueryProtocolService prometheus,
            OpsPrometheusSettings settings) {
        if (prometheus == null) throw new IllegalArgumentException("PROMETHEUS_QUERY_SERVICE_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("PROMETHEUS_SETTINGS_REQUIRED");
        this.prometheus = prometheus;
        this.settings = settings;
    }

    public ScheduledTaskScreeningResult screen(TaskScheduleRuntimeConfiguration runtime) {
        if (runtime == null) return ScheduledTaskScreeningResult.configError("巡检缺少运行配置");
        TaskScheduleScreeningConfiguration recipe = runtime.screening();
        if (recipe == null || !recipe.enabled()) {
            return ScheduledTaskScreeningResult.deepRequired("未启用任务级轻量筛查规则，转入深度调查");
        }
        if (!"PROMETHEUS".equals(recipe.sourceType())) {
            return ScheduledTaskScreeningResult.configError("当前轻量筛查仅支持 PROMETHEUS，配置为：" + recipe.sourceType());
        }
        if (!recipe.hasBusinessCondition()) {
            return ScheduledTaskScreeningResult.configError("已启用轻量筛查，但没有配置任何业务阈值");
        }
        if (settings.baseUrl().isBlank()) {
            return ScheduledTaskScreeningResult.configError("Prometheus 未配置，无法执行任务级筛查");
        }

        try {
            OpsPrometheusQueryProtocolService.Result result = prometheus.execute(
                    new OpsPrometheusQueryProtocolService.Input(
                            settings.baseUrl(),
                            settings.jobName(),
                            settings.timeoutSeconds(),
                            text(runtime.promWindow(), "5m"),
                            recipe.primaryUri()),
                    () -> {
                    });
            return evaluate(recipe, result);
        } catch (Exception error) {
            return new ScheduledTaskScreeningResult(
                    ScheduledTaskScreeningResult.Status.INCONCLUSIVE,
                    "Prometheus 任务级筛查查询失败，不能判定为正常：" + safeMessage(error),
                    List.of("prometheus"));
        }
    }

    private ScheduledTaskScreeningResult evaluate(
            TaskScheduleScreeningConfiguration recipe,
            OpsPrometheusQueryProtocolService.Result result) {
        List<String> breaches = new ArrayList<>();
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("instanceTotal", result.instanceTotal());
        observation.put("instanceUp", result.instanceUp());
        observation.put("errorRatePercent", result.errorRate());
        observation.put("cpuPercent", result.processCpuUsagePercent());
        observation.put("heapPercent", result.heapMemoryUsagePercent());

        if (recipe.maxErrorRatePercent() != null && result.errorRate() > recipe.maxErrorRatePercent()) {
            breaches.add("5xx错误率 " + result.errorRate() + "% > " + recipe.maxErrorRatePercent() + "%");
        }
        if (recipe.maxCpuPercent() != null && result.processCpuUsagePercent() > recipe.maxCpuPercent()) {
            breaches.add("CPU " + result.processCpuUsagePercent() + "% > " + recipe.maxCpuPercent() + "%");
        }
        if (recipe.maxHeapPercent() != null && result.heapMemoryUsagePercent() > recipe.maxHeapPercent()) {
            breaches.add("Heap " + result.heapMemoryUsagePercent() + "% > " + recipe.maxHeapPercent() + "%");
        }
        if (recipe.minInstanceUpRatio() != null) {
            if (result.instanceTotal() <= 0) {
                return new ScheduledTaskScreeningResult(
                        ScheduledTaskScreeningResult.Status.INCONCLUSIVE,
                        "Prometheus 没有返回可核验的实例 UP 样本，不能判定为正常：" + observation,
                        List.of("prometheus"));
            }
            double ratio = ((double) result.instanceUp()) / result.instanceTotal();
            observation.put("instanceUpRatio", ratio);
            if (ratio < recipe.minInstanceUpRatio()) {
                breaches.add("实例在线率 " + round(ratio) + " < " + recipe.minInstanceUpRatio());
            }
        }

        if (breaches.isEmpty()) {
            return ScheduledTaskScreeningResult.normal("任务级 Prometheus 筛查正常：" + observation);
        }
        return ScheduledTaskScreeningResult.abnormal(
                "任务级 Prometheus 筛查发现异常：" + String.join("；", breaches),
                List.of("prometheus"));
    }

    private double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        }
        return error.getMessage().trim();
    }
}
