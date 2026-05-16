package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presents an already-decided report; it cannot change its thresholds or verdict. */
final class OpsObservabilityReportFormatter {
    @SuppressWarnings("unchecked")
    String format(Map<String,Object> report) {
        var window = (Map<String,Object>) report.get("window");
        var metrics = (Map<String,Object>) report.get("metrics");
        String verdict = switch (String.valueOf(report.get("status"))) {
            case "HEALTHY" -> "已检查指标满足 SLO";
            case "UNHEALTHY" -> "已检查指标未满足 SLO";
            case "OBSERVED_ANOMALY" -> "已观测到异常";
            case "NO_OBSERVED_ANOMALY" -> "已检查指标未见异常";
            case "UNREACHABLE" -> "采集目标不可达";
            case "CONFLICT" -> "证据存在冲突";
            default -> "无法判定";
        };
        boolean defined = Boolean.TRUE.equals(metrics.get("requestMetricsDefined"));
        var text = new StringBuilder("### 观测结论：").append(verdict).append("\n\n")
                .append("服务：`").append(window.get("serviceId")).append("` · 项目：`").append(window.get("projectId")).append("`\n\n")
                .append("实际窗口（UTC）：").append(window.get("startTime")).append(" 至 ").append(window.get("endTime")).append("。\n\n");
        if (!Boolean.TRUE.equals(window.get("complete"))) text.append("告警后窗口尚缺 ").append(window.get("missingFutureSeconds")).append(" 秒，当前结论受限。\n\n");
        text.append("| 检查项 | 观测值 | 已发布标准 |\n|---|---:|---|\n")
                .append("| 采集目标 | ").append(metrics.get("reachability")).append(" | 不可达独立列出 |\n")
                .append("| 请求数下界 | ").append(metrics.get("sampleCountLowerBound")).append(" | 至少 100 |\n")
                .append("| 错误率 | ").append(defined ? number(metrics.get("errorRate"),100) + "%" : "未定义").append(" | ≤ 1% |\n")
                .append("| 整体 p95 | ").append(Boolean.TRUE.equals(metrics.get("p95Defined")) ? number(metrics.get("p95Seconds"),1) + " 秒" : "未定义").append(" | ≤ 1 秒 |\n\n");
        if ("FOUND".equals(report.get("slowSqlFinding"))) text.append("同一窗口中已发现慢 SQL。")
                .append(Boolean.TRUE.equals(report.get("explainAvailable")) ? "已读取对应查询模板的执行计划。" : "执行计划尚未取得。")
                .append("这支持继续调查该线索，根因尚未证实。\n\n");
        append(text,"证据缺口",report.get("evidenceGaps"));
        append(text,"证据冲突",report.get("evidenceConflicts"));
        append(text,"查询记录",report.get("evidenceReferences"));
        text.append("下一步：").append(report.get("nextStep")).append("。\n\n")
                .append("CPU、JVM GC 尚未接入；本结论只覆盖表内指标。报告保留原始结构化判定和工具证据，未执行业务修复。\n");
        if (report.get("investigation") instanceof Map<?, ?> investigation) {
            text.append("\n### 异常后的 B 调查\n\n子运行：`").append(investigation.get("childRunId"))
                    .append("`，工作流 `").append(investigation.get("workflowId")).append("` v")
                    .append(investigation.get("workflowVersion")).append("。子运行执行完成不代表服务健康或根因确认。\n\n")
                    .append(investigation.get("output")).append("\n");
        }
        return text.toString();
    }
    private String number(Object value,double scale) { return String.format(Locale.ROOT,"%.3f",((Number)value).doubleValue()*scale); }
    private void append(StringBuilder text,String title,Object values) {
        if (!(values instanceof List<?> items) || items.isEmpty()) return;
        text.append(title).append("：\n\n");
        for(Object item:items) text.append("- `").append(String.valueOf(item).replace("`","")).append("`\n");
        text.append("\n");
    }
}
