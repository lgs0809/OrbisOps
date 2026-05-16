package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.List;
import java.util.Optional;

/** AI final-analysis prompt materialization boundary. */
final class OpsAnalysisAiPromptBuilder {

    String build(OpsAnalysisResponseDTO response) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是生产业务系统的运维主 Agent。请基于下面的主 Agent 调查计划、子 Agent 调查结果和已采集数据生成分析报告。");
        prompt.append("不要把采集数据原样罗列成流水账，要给出判断、风险级别、可能原因和下一步排查建议。");
        prompt.append("如果某个数据源未选择、查不到或证据不足，要明确说明原因；不要假装查过未选择的数据源。\n\n");
        prompt.append("分析目标：分析当前业务系统最近运行状态，重点关注接口 QPS、响应时间、错误率、实例在线情况、日志 ERROR/WARN 和业务风险。\n\n");
        prompt.append(response.getMarkdownReport()).append("\n");
        prompt.append("### 最近日志样本\n\n");
        recentLogs(response).forEach(log -> prompt
                .append("- [").append(log.getLevel()).append("] ")
                .append(log.getTimestamp()).append(" ")
                .append(log.getLoggerName()).append("：")
                .append(log.getMessage()).append('\n'));
        prompt.append("\n### MySQL 慢 SQL 样本\n\n");
        slowSqlSamples(response).forEach(sample -> prompt
                .append("- timeMs=").append(value(sample.getQueryTimeMs()))
                .append(", rowsExamined=").append(value(sample.getRowsExamined()))
                .append(", count=").append(value(sample.getCountStar()))
                .append(", sql=").append(abbreviate(sample.getSqlText(), 240))
                .append('\n'));
        prompt.append("\n请输出：\n");
        prompt.append("1. 运行状态结论。\n");
        prompt.append("2. 关键数据解读。\n");
        prompt.append("3. 异常或风险判断。\n");
        prompt.append("4. 具体排查或优化建议。\n");
        prompt.append("5. 本次分析使用了哪些真实数据源。\n");
        return prompt.toString();
    }

    private List<OpsAnalysisResponseDTO.LogSampleDTO> recentLogs(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getRecentLogs()).orElse(List.of());
    }

    private List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> slowSqlSamples(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getSlowSqlSamples()).orElse(List.of());
    }

    private String value(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
