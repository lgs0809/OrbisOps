package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.List;

/** Final-report-specific LLM prompt, JSON, and output validation boundary. */
final class OpsFinalReportLlmProtocolService {

    private static final String AGENT_NAME = "ops-final-report";
    private static final String TRUNCATED_SUFFIX =
            "\n\n> 报告过长，已按配置截断。";

    private final OpsAgentLlmClient llmClient;

    OpsFinalReportLlmProtocolService(OpsAgentLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    Result generate(Input input) {
        JSONObject json = llmClient.chatJsonObject(
                AGENT_NAME,
                """
                        你是生产业务系统的运维主 Agent，负责生成最终排障报告。
                        只能基于输入中的真实数据源、子 Agent 结果、证据和缺口写报告，不得编造未查询的数据。
                        如果证据不足，要明确写出缺口和下一步需要补充什么。
                        请返回严格 JSON：{"markdownReport":"..."}，markdownReport 必须是可直接发送给运维人员的 Markdown。
                        """,
                """
                        请基于下面输入生成最终运维分析结果。

                        分析 ID：%s
                        生成时间：%s
                        日志窗口：最近 %s 分钟
                        PromQL 窗口：%s

                        输出结构：
                        1. # 运维分析结果
                        2. ## 结论
                        3. ## 关键证据
                        4. ## 风险与可能原因
                        5. ## 建议动作
                        6. ## 数据源与缺口

                        真实证据快照：
                        %s
                        """.formatted(
                        input.analysisId(),
                        input.generatedAt(),
                        input.rangeMinutes(),
                        input.promWindow(),
                        input.evidenceSnapshot()),
                List.of());
        if (json == null) {
            return Result.noOutput();
        }
        String markdownReport = json.getString("markdownReport");
        if (!StringUtils.hasText(markdownReport)) {
            return Result.rejected("最终报告 JSON 缺少 markdownReport");
        }
        markdownReport = markdownReport.trim();
        if (markdownReport.length() > input.maxChars()) {
            markdownReport = markdownReport.substring(
                    0,
                    Math.max(0, input.maxChars())) + TRUNCATED_SUFFIX;
        }
        if (!markdownReport.contains("##") || markdownReport.length() < 80) {
            return Result.rejected(
                    "最终报告输出格式不符合预期，analysisId="
                            + input.analysisId());
        }
        return Result.success(markdownReport);
    }

    void rejectDegradation(String reason) {
        llmClient.rejectDegradation(AGENT_NAME, reason);
    }

    record Input(
            String analysisId,
            String generatedAt,
            Integer rangeMinutes,
            String promWindow,
            String evidenceSnapshot,
            int maxChars) {
    }

    record Result(
            String markdownReport,
            String rejectionReason) {

        static Result noOutput() {
            return new Result(null, null);
        }

        static Result rejected(String reason) {
            return new Result(null, reason);
        }

        static Result success(String markdownReport) {
            return new Result(markdownReport, null);
        }

        boolean hasReport() {
            return StringUtils.hasText(markdownReport);
        }

        boolean rejected() {
            return StringUtils.hasText(rejectionReason);
        }
    }
}
