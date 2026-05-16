package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.Map;

/** Presentation only: C's verdict remains the deterministic policy result. */
final class OpsChangeVerificationReportFormatter {
    String format(Map<String,Object> report) {
        String verdict=switch(String.valueOf(report.get("status"))) { case "PASS" -> "通过"; case "FAIL" -> "不通过"; default -> "无法判定"; };
        var text=new StringBuilder("### 变更后只读验收：").append(verdict).append("\n\n");
        if (report.get("changeRef") instanceof Map<?,?> ref) text.append("变更包：`").append(ref.get("packageId"))
                .append("`；批准版本：").append(ref.containsKey("approvedVersion") ? ref.get("approvedVersion") : "尚未进入批准快照核验").append("。\n\n");
        text.append("执行完成与验收通过分别记录。默认前后各十五分钟、各至少一百个真实请求；完整窗口与实际版本缺失时不判通过。\n\n");
        append(text,"未通过检查",report.get("failedChecks")); append(text,"证据缺口",report.get("evidenceGaps"));
        if (report.containsKey("nextObservationAt")) text.append("下一观察时刻（UTC）：").append(report.get("nextObservationAt")).append("。\n\n");
        if (report.containsKey("actualVersion")) text.append("实际版本：`").append(report.get("actualVersion")).append("`。\n\n");
        for (String stage:List.of("before","after")) {
            if (report.get(stage) instanceof Map<?,?> observation) {
                text.append(stage.equals("before") ? "#### 变更前\n\n" : "#### 变更后\n\n");
                @SuppressWarnings("unchecked") var typed=(Map<String,Object>)observation;
                text.append(new OpsObservabilityReportFormatter().format(typed)).append("\n");
            }
        }
        append(text,"查询记录",report.get("evidenceReferences"));
        text.append("下一步：").append(report.get("nextStep")).append("。\n\n")
                .append("本流程只读，不执行回滚；观察结果只说明约定窗口的状态，不能单凭前后变化证明发布的因果关系。\n");
        return text.toString();
    }
    private void append(StringBuilder text,String label,Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) return;
        text.append(label).append("：\n\n");
        for (Object item:list) {
            String raw=String.valueOf(item).replace("`","");
            String description=switch(raw) {
                case "CHANGE_NOT_LANDED" -> "方案尚未完成实际落地执行，不能开始变更后验收";
                case "POST_CHANGE_WINDOW_NOT_COMPLETE" -> "变更后的观察时间窗尚未完整，请等待下一观察时刻";
                case "PREAPPROVED_OBSERVABILITY_CRITERIA_REQUIRED" -> "批准方案中缺少业务观测验收标准，需要重新准备并审批，不能事后补填";
                case "PREAPPROVED_LOAD_COMPARABILITY_REQUIRED" -> "批准方案未约定可比较的负载范围";
                default -> "";
            };
            text.append("- ").append(description.isBlank() ? "`"+raw+"`" : description+"（`"+raw+"`）").append("\n");
        }
        text.append("\n");
    }
}
