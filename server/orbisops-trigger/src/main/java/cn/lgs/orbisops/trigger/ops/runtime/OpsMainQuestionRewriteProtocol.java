package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Plain prompt and JSON projection protocol for standalone-question rewriting. */
final class OpsMainQuestionRewriteProtocol {

    private static final Set<String> ACTION_CLASSIFICATIONS =
            Set.of("DIAGNOSE", "REMEDIATE", "VERIFY", "ABSTAIN");

    String systemPrompt() {
        return """
                你是运维主 Agent 的问题语义标准化器，负责在规划、路由、RAG 和 MCP 调用之前，把用户最新一轮输入改写成独立、明确、可检索的问题，并单独判断用户本轮请求的任务类型。

                目标：
                - 结合会话记忆解析“这个、那个、上面、刚才、前面的、它、继续查”等指代；
                - 保留 traceId、orderId、URI、错误码、时间窗口、服务名、指标名等可检索线索；
                - 用户本轮显式给出的业务对象名、业务动作名、页面/功能名称也是资源检索锚点，必须原样保留；不得为了让句子更通顺而删成“该故障/该服务/该问题”等泛化指代；
                - 不新增用户本轮没有表达的事实或状态判断，不得把“可能/看起来/是否”强化成“已确认/仍在持续/已经定位”，不编造数据源结果；
                - 不得根据业务名称或会话记忆猜测、补全、导入或改写 URI/API path；只有用户本轮输入里已经明确出现的 URI/API path 才能保留。记忆中的技术标识只能帮助理解指代，不能写入重写结果；业务名称到 API 的映射由下游 OpenAPI MCP 完成；
                - 如果用户问题已经独立清晰，返回原问题。
                - actionClassification 只描述用户本轮请求的任务语义，不根据后续证据是否充分、验证是否成功来改变：
                  - DIAGNOSE：只读排查、解释当前异常/性能/错误/原因，未明确要求进入受控处置链；
                  - REMEDIATE：明确要求 Incident 记录/跟进、ChangePackage、受控修复、重启、回滚或其他后续处置；
                  - VERIFY：明确要求确认某次处理、修复、变更或恢复动作之后当前是否已经恢复/是否达到验证条件；
                  - ABSTAIN：用户请求绕过审批、安全边界或直接执行禁止的生产副作用。
                - “证据不足/无法确认”是后续 evidenceCompleteness / verificationStatus 的结论，不得把 DIAGNOSE 自动改成 VERIFY；同理，VERIFY 即使证据不足仍然是 VERIFY。

                只输出 JSON，不要输出 Markdown 或解释：
                {
                  "rewrittenQuestion": "独立问题",
                  "changed": true,
                  "reason": "为什么改写或不改写",
                  "resolvedReferences": ["这个=上一轮提到的某个接口或资源"],
                  "actionClassification": "DIAGNOSE|REMEDIATE|VERIFY|ABSTAIN"
                }
                """;
    }

    String userPrompt(
            String query,
            String memoryContext,
            OpsMainQuestionRewriteSettings settings) {
        return """
                会话记忆：
                %s

                用户最新输入：
                %s

                请输出 JSON。
                """.formatted(
                OpsMemoryTextUtils.abbreviate(
                        memoryContext,
                        settings.memoryPromptLimit()),
                OpsMemoryTextUtils.abbreviate(
                        query,
                        settings.questionPromptLimit()));
    }

    OpsMainQuestionRewriteService.RewriteResult project(
            String original,
            JSONObject json,
            OpsMainQuestionRewriteSettings settings) {
        if (json == null) {
            return OpsMainQuestionRewriteService.RewriteResult.unchanged(
                    original,
                    "query rewrite llm unavailable");
        }
        String rewritten = normalize(firstText(
                json.getString("rewrittenQuestion"),
                json.getString("standaloneQuestion"),
                json.getString("query")));
        if (!StringUtils.hasText(rewritten)) {
            return OpsMainQuestionRewriteService.RewriteResult.unchanged(
                    original,
                    "query rewrite returned blank question");
        }
        int limit = settings.rewrittenQuestionLimit();
        if (rewritten.length() > limit) {
            rewritten = rewritten.substring(0, limit).trim();
        }
        boolean changed = Boolean.TRUE.equals(json.getBoolean("changed"))
                || !rewritten.equals(original);
        String reason = firstText(
                json.getString("reason"),
                changed
                        ? "resolved conversation references"
                        : "already standalone");
        return new OpsMainQuestionRewriteService.RewriteResult(
                original,
                rewritten,
                changed,
                reason,
                references(json),
                actionClassification(json));
    }

    String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> references(JSONObject json) {
        List<String> references = new ArrayList<>();
        if (json.getJSONArray("resolvedReferences") == null) {
            return references;
        }
        for (int i = 0; i < json.getJSONArray("resolvedReferences").size(); i++) {
            String value = json.getJSONArray("resolvedReferences").getString(i);
            if (StringUtils.hasText(value)) {
                references.add(OpsMemoryTextUtils.abbreviate(value.trim(), 160));
            }
        }
        return List.copyOf(references);
    }

    private String actionClassification(JSONObject json) {
        if (json == null) return "";
        String value = normalize(json.getString("actionClassification")).toUpperCase(Locale.ROOT);
        return ACTION_CLASSIFICATIONS.contains(value) ? value : "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
