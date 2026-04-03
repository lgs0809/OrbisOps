package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.Collection;

/** Plain protocol helper for extracting, validating and repairing LLM JSON output. */
final class OpsLlmJsonProtocol {

    ParseResult parse(String content, int maxOutputChars) {
        String json = extract(content);
        if (!hasText(json)) {
            return ParseResult.failed(
                    "未返回 JSON 内容：" + abbreviate(content, 600),
                    null);
        }
        if (json.length() > maxOutputChars) {
            return ParseResult.failed(
                    "JSON 输出超过上限，length=" + json.length()
                            + ", maxOutputChars=" + maxOutputChars,
                    null);
        }
        try {
            return ParseResult.success(JSON.parseObject(json));
        } catch (Exception e) {
            return ParseResult.failed(
                    "JSON 解析失败：" + e.getMessage()
                            + "，内容：" + abbreviate(json, 600),
                    e);
        }
    }

    boolean shouldRetryWithEagerSkillContext(
            ParseResult result,
            Collection<String> skillNames,
            boolean enabled) {
        return enabled
                && result != null
                && result.json() == null
                && result.reason() != null
                && result.reason().startsWith("未返回 JSON 内容")
                && skillNames != null
                && !skillNames.isEmpty();
    }

    String repairSystemPrompt() {
        return """
                你是运维 Agent 的 JSON 自修复器。
                你必须重新完成原始任务，并只输出一个完整、可解析的 JSON 对象。
                不要输出 Markdown、解释、代码块或 JSON 之外的任何字符。
                所有字符串字段必须简短，单个字段不超过 80 个中文字符；数组最多 2 项。
                """;
    }

    String repairUserPrompt(
            String originalSystemPrompt,
            String originalUserPrompt,
            String invalidOutput,
            String reason) {
        return """
                原始系统要求：
                %s

                原始用户输入：
                %s

                上一次输出不是合法 JSON，原因：%s

                上一次输出片段：
                %s

                请按原始 JSON schema 重新输出一个完整、简短、合法的 JSON 对象。
                """.formatted(
                originalSystemPrompt,
                originalUserPrompt,
                reason,
                abbreviate(invalidOutput, 1200));
    }

    private String extract(String content) {
        if (!hasText(content)) {
            return null;
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    record ParseResult(JSONObject json, String reason, Exception exception) {

        static ParseResult success(JSONObject json) {
            return new ParseResult(json, "", null);
        }

        static ParseResult failed(String reason, Exception exception) {
            return new ParseResult(null, reason, exception);
        }
    }
}
