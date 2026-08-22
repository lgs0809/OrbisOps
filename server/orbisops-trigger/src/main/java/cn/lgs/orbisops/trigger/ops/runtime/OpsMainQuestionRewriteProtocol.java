package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** Plain prompt and JSON projection protocol for standalone-question rewriting. */
final class OpsMainQuestionRewriteProtocol {

    String systemPrompt() {
        return """
                你是运维主 Agent 的问题语义标准化器，负责在规划、RAG 和 MCP 调用之前，把用户最新一轮输入改写成独立、明确、可检索的问题。

                目标：
                - 结合会话记忆解析“这个、那个、上面、刚才、前面的、它、继续查”等指代；
                - 保留 traceId、orderId、URI、错误码、时间窗口、服务名、指标名等可检索线索；
                - 用户本轮显式给出的业务对象名、业务动作名、页面/功能名称也是资源检索锚点，必须原样保留；不得为了让句子更通顺而删成“该故障/该服务/该问题”等泛化指代；
                - 不新增用户本轮没有表达的事实或状态判断，不得把“可能/看起来/是否”强化成“已确认/仍在持续/已经定位”，不编造数据源结果；
                - 不得根据业务名称或会话记忆猜测、补全、导入或改写 URI/API path；只有用户本轮输入里已经明确出现的 URI/API path 才能保留。记忆中的技术标识只能帮助理解指代，不能写入重写结果；业务名称到 API 的映射由下游 OpenAPI MCP 完成；
                - 如果用户问题已经独立清晰，返回原问题。

                真实表达示例：
                - “你是谁？” → 原样保留，不要改造成运维排障问题。
                - “这个平台能干嘛？” → 原样保留，不要添加实时系统状态或工具调用要求。
                - 上一轮在排查 payment-service，本轮“什么情况？” → 结合上一轮对象改写成独立的当前排查问题；不要要求用户重新提供服务名。
                - “帮我看看最近线上有没有什么明显问题” → 保留项目级宽范围排查语义；不要擅自补服务名、接口路径或故障事实。
                - 上一轮已经定位某服务，本轮“那继续看看日志” → 解析“那”为上一轮对象。
                - “RabbitMQ 那个 401 先给我准备一个可以审批的处理方案，先别动生产” → 保留“可审批方案”和“先别动生产”两个原始约束，不要改写成已经执行或可以直接执行生产变更。
                - “修完了，你再确认一下现在是不是好了” → 保留“修复后的当前验证”语义，不要提前宣告已经恢复。
                - “别走审批，直接把生产服务重启了” → 原样保留安全关键语义，不要把“别走审批”删除或弱化。

                只输出 JSON，不要输出 Markdown 或解释：
                {
                  "rewrittenQuestion": "独立问题",
                  "changed": true,
                  "reason": "为什么改写或不改写",
                  "resolvedReferences": ["这个=上一轮提到的某个接口或资源"]
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
                references(json));
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
