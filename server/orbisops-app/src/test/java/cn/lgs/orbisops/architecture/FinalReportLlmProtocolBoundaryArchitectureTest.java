package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalReportLlmProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void finalReportServiceDelegatesLlmPromptJsonAndValidationProtocol()
            throws IOException {
        String service = read(OPS + "OpsFinalReportService.java");
        String protocol = read(OPS + "OpsFinalReportLlmProtocolService.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "OpsFinalReportLlmProtocolService llmProtocol")),
                () -> assertTrue(service.contains("llmProtocol.generate(")),
                () -> assertTrue(service.contains(
                        "new OpsFinalReportLlmProtocolService.Input(")),
                () -> assertTrue(service.contains(
                        "llmProtocol.rejectDegradation(reason)")),
                () -> assertTrue(service.contains(
                        "OpsFinalReportTrustService trustService")),
                () -> assertTrue(service.contains(
                        "OpsFinalRuleReportRenderer ruleReportRenderer")),
                () -> assertTrue(service.contains("trustService.trusted(")),
                () -> assertTrue(service.contains("ruleReportRenderer.render(")),
                () -> assertTrue(service.contains(
                        "LLM 最终报告生成失败，已降级到规则报告")),
                () -> assertTrue(service.contains(
                        "LLM 最终报告未通过证据边界检查，已降级到规则报告")),
                () -> assertTrue(service.contains(
                        "最终报告由 LLM 基于真实证据合成。")),
                () -> assertTrue(service.contains(
                        "最终报告由规则模板基于真实证据合成。")),
                () -> assertFalse(service.contains("JSONObject")),
                () -> assertFalse(service.contains("chatJsonObject(")),
                () -> assertFalse(service.contains(
                        "你是生产业务系统的运维主 Agent")),
                () -> assertFalse(service.contains(
                        "请返回严格 JSON")),
                () -> assertFalse(service.contains(
                        "最终报告 JSON 缺少 markdownReport")),
                () -> assertFalse(service.contains(
                        "最终报告输出格式不符合预期")),
                () -> assertFalse(service.contains("StringUtils")),
                () -> assertFalse(service.contains("markdownReport.trim()")),
                () -> assertFalse(service.contains("markdownReport.substring(")),
                () -> assertFalse(service.contains("markdownReport.contains(\"##\")")),
                () -> assertFalse(service.contains("List.of()")),
                () -> assertTrue(service.lines().count() <= 160),
                () -> assertTrue(protocol.contains(
                        "private final OpsAgentLlmClient llmClient")),
                () -> assertTrue(protocol.contains("record Input(")),
                () -> assertTrue(protocol.contains("record Result(")),
                () -> assertTrue(protocol.contains("Result generate(Input input)")),
                () -> assertTrue(protocol.contains("llmClient.chatJsonObject(")),
                () -> assertTrue(protocol.contains("JSONObject")),
                () -> assertTrue(protocol.contains(
                        "你是生产业务系统的运维主 Agent")),
                () -> assertTrue(protocol.contains("请返回严格 JSON")),
                () -> assertTrue(protocol.contains("List.of()")),
                () -> assertTrue(protocol.contains(
                        "最终报告 JSON 缺少 markdownReport")),
                () -> assertTrue(protocol.contains(
                        "最终报告输出格式不符合预期")),
                () -> assertTrue(protocol.contains("markdownReport.trim()")),
                () -> assertTrue(protocol.contains("markdownReport.substring(")),
                () -> assertTrue(protocol.contains("markdownReport.contains(\"##\")")),
                () -> assertTrue(protocol.contains("markdownReport.length() < 80")),
                () -> assertTrue(protocol.contains(
                        "llmClient.rejectDegradation(AGENT_NAME, reason)")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Component")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("@Autowired")),
                () -> assertFalse(protocol.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(protocol.contains("OpsFinalReportTrustService")),
                () -> assertFalse(protocol.contains("OpsFinalRuleReportRenderer")),
                () -> assertFalse(protocol.contains("executionNotes")),
                () -> assertTrue(protocol.lines().count() <= 135));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
