package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningSignals;

import java.util.Locale;
import java.util.regex.Pattern;

/** Classifies stable question signals used by deterministic planning. */
final class InvestigationPlanningSignalPolicy {

    private static final Pattern EXPLICIT_LOG_LEVEL_PATTERN =
            Pattern.compile("\\b(ERROR|WARN|WARNING|Exception|StackTrace)\\b");
    private static final Pattern BUSINESS_ERROR_CODE_PATTERN =
            Pattern.compile("\\b(?:ERR|ERROR|BIZ|SYS|SYSTEM|E)[-_][A-Z0-9]{2,}\\b");

    boolean pureKnowledgeQuestion(InvestigationPlanningSignals signals) {
        return signals != null
                && ((signals.knowledgeSignal()
                && !signals.logSignal()
                && !signals.metricSignal()
                && !signals.slowSqlSignal())
                || isKnowledgeExplanationQuestion(signals));
    }

    boolean isKnowledgeExplanationQuestion(InvestigationPlanningSignals signals) {
        if (!signals.knowledgeSignal() || signals.traceIdPresent() || signals.orderIdPresent()) {
            return false;
        }
        String question = value(signals.loweredQuestion());
        boolean asksForExplanation = containsAny(question,
                "指标含义", "含义", "解释", "是什么", "知识库", "指标字典",
                "日志字典", "sop", "runbook", "历史案例", "如何排查", "怎么排查",
                "如何", "怎么写", "怎么做", "能不能", "可以", "应该", "规则", "建议",
                "自动", "安全", "只读", "边界", "策略", "模板", "包含", "什么时候");
        boolean asksForRealtime = containsAny(question,
                "最近", "当前", "现在", "趋势", "升高", "上涨", "下降",
                "怎么样", "多少", "是否", "证据", "有没有", "帮我看", "看一下",
                "日志证据", "查日志", "优先看 es", "es 日志");
        return asksForExplanation && !asksForRealtime;
    }

    boolean hasStrongLogSignal(InvestigationPlanningSignals signals) {
        if (signals == null) {
            return false;
        }
        String original = value(signals.originalQuestion());
        String question = value(signals.loweredQuestion());
        boolean negativeLogIntent = containsAny(
                question,
                "不需要最近日志", "不需要日志", "无需日志", "不要日志", "不查日志");
        boolean explicitRuntimeFilter = signals.traceIdPresent()
                || signals.orderIdPresent()
                || BUSINESS_ERROR_CODE_PATTERN.matcher(original).find()
                || EXPLICIT_LOG_LEVEL_PATTERN.matcher(original).find();
        if (negativeLogIntent && !explicitRuntimeFilter) {
            return false;
        }
        return explicitRuntimeFilter
                || containsAny(question, "日志", "错误码", "堆栈", "报错", "查日志", "日志证据");
    }

    boolean explicitChangeRequest(InvestigationPlanningSignals signals) {
        if (signals == null) {
            return false;
        }
        String question = value(signals.loweredQuestion());
        boolean packageNegated = containsAny(question,
                "不要创建 changepackage", "不要创建changepackage", "不创建 changepackage", "不创建changepackage",
                "不要创建变更包", "不创建变更包", "无需变更包");
        boolean packageRequested = !packageNegated && containsAny(question,
                "创建 changepackage", "创建changepackage", "生成 changepackage", "生成changepackage",
                "提交 changepackage", "提交changepackage", "创建变更包", "生成变更包", "提交变更包");
        if (packageRequested) {
            return true;
        }

        boolean analysisOnly = containsAny(question,
                "只分析", "仅分析", "只排查", "仅排查", "只诊断", "仅诊断",
                "不要修复", "不修复", "不要执行变更", "不执行变更", "不要操作", "不操作");
        if (analysisOnly) {
            return false;
        }
        return containsAny(question,
                "请修复", "直接修复", "帮我修复", "请调整", "执行调整",
                "请重启", "直接重启", "执行重启", "帮我重启", "restart_service",
                "请发布", "执行发布", "请回滚", "执行回滚", "请清理", "执行清理");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
