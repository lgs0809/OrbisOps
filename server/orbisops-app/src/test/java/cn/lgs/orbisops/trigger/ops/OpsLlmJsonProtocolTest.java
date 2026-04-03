package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmJsonProtocolTest {

    private final OpsLlmJsonProtocol protocol = new OpsLlmJsonProtocol();

    @Test
    void extractsJsonFromCodeFenceAndSurroundingText() {
        OpsLlmJsonProtocol.ParseResult fenced = protocol.parse(
                "```json\n{\"status\":\"ok\"}\n```",
                1000);
        OpsLlmJsonProtocol.ParseResult surrounded = protocol.parse(
                "说明文字 {\"status\":\"ok\"} 结尾",
                1000);

        assertEquals("ok", fenced.json().getString("status"));
        assertEquals("ok", surrounded.json().getString("status"));
        assertEquals("", fenced.reason());
        assertNull(fenced.exception());
    }

    @Test
    void blankOrNonJsonContentKeepsLegacyReasonAndAbbreviation() {
        OpsLlmJsonProtocol.ParseResult blank = protocol.parse("   ", 1000);
        OpsLlmJsonProtocol.ParseResult text = protocol.parse("not json", 1000);
        String longText = "x".repeat(700);
        OpsLlmJsonProtocol.ParseResult longResult = protocol.parse(longText, 1000);

        assertNull(blank.json());
        assertTrue(blank.reason().startsWith("未返回 JSON 内容："));
        assertTrue(text.reason().contains("not json"));
        assertTrue(longResult.reason().endsWith("..."));
        assertTrue(longResult.reason().length() < longText.length());
    }

    @Test
    void enforcesExtractedJsonLengthBeforeParsing() {
        OpsLlmJsonProtocol.ParseResult result = protocol.parse(
                "{\"value\":\"1234567890\"}",
                10);

        assertNull(result.json());
        assertTrue(result.reason().contains("JSON 输出超过上限"));
        assertTrue(result.reason().contains("maxOutputChars=10"));
        assertNull(result.exception());
    }

    @Test
    void invalidJsonReturnsParserExceptionAndContentFragment() {
        OpsLlmJsonProtocol.ParseResult result = protocol.parse(
                "{invalid-json}",
                1000);

        assertNull(result.json());
        assertTrue(result.reason().startsWith("JSON 解析失败："));
        assertTrue(result.reason().contains("{invalid-json}"));
        assertNotNull(result.exception());
    }

    @Test
    void eagerSkillRetryOnlyAppliesToMissingJsonWithEnabledSkills() {
        OpsLlmJsonProtocol.ParseResult missing = protocol.parse("plain text", 1000);
        OpsLlmJsonProtocol.ParseResult invalid = protocol.parse("{invalid}", 1000);

        assertTrue(protocol.shouldRetryWithEagerSkillContext(
                missing,
                List.of("runtime-ops"),
                true));
        assertFalse(protocol.shouldRetryWithEagerSkillContext(
                missing,
                List.of("runtime-ops"),
                false));
        assertFalse(protocol.shouldRetryWithEagerSkillContext(
                missing,
                List.of(),
                true));
        assertFalse(protocol.shouldRetryWithEagerSkillContext(
                invalid,
                List.of("runtime-ops"),
                true));
        assertFalse(protocol.shouldRetryWithEagerSkillContext(
                null,
                List.of("runtime-ops"),
                true));
    }

    @Test
    void repairPromptsPreserveOriginalContextAndAbbreviateInvalidOutput() {
        String system = protocol.repairSystemPrompt();
        String user = protocol.repairUserPrompt(
                "original system",
                "original user",
                "x".repeat(1400),
                "invalid reason");

        assertTrue(system.contains("JSON 自修复器"));
        assertTrue(system.contains("只输出一个完整、可解析的 JSON 对象"));
        assertTrue(user.contains("original system"));
        assertTrue(user.contains("original user"));
        assertTrue(user.contains("invalid reason"));
        assertTrue(user.contains("..."));
        assertFalse(user.contains("x".repeat(1300)));
    }
}
