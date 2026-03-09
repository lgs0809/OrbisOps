package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalRuleReportRendererBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void finalReportServiceDelegatesDeterministicMarkdownFallbackRendering()
            throws IOException {
        String service = read(OPS + "OpsFinalReportService.java");
        String renderer = read(OPS + "OpsFinalRuleReportRenderer.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "OpsFinalRuleReportRenderer ruleReportRenderer")),
                () -> assertTrue(service.contains("ruleReportRenderer.render(")),
                () -> assertTrue(service.contains(
                        "最终报告由规则模板基于真实证据合成。")),
                () -> assertTrue(service.contains(
                        "OpsFinalReportLlmProtocolService llmProtocol")),
                () -> assertTrue(service.contains(
                        "OpsFinalReportTrustService trustService")),
                () -> assertTrue(service.contains("rejectReport(")),
                () -> assertFalse(service.contains(
                        "report.append(\"# 运维分析结果")),
                () -> assertFalse(service.contains(
                        "report.append(\"## 结论")),
                () -> assertFalse(service.contains(
                        "report.append(\"## 建议动作")),
                () -> assertFalse(service.contains(
                        "report.append(\"\\n## 证据快照")),
                () -> assertFalse(service.contains(
                        "暂未形成明确结论，需要补充实时数据或知识库证据")),
                () -> assertFalse(service.contains("StringBuilder report")),
                () -> assertFalse(service.contains("Optional")),
                () -> assertFalse(service.contains(".limit(2)")),
                () -> assertFalse(service.contains("private String truncate(")),
                () -> assertTrue(service.lines().count() <= 185),
                () -> assertTrue(renderer.contains(
                        "String render(")),
                () -> assertTrue(renderer.contains("appendConclusions(")),
                () -> assertTrue(renderer.contains("appendActions(")),
                () -> assertTrue(renderer.contains("appendSourcesAndGaps(")),
                () -> assertTrue(renderer.contains("# 运维分析结果")),
                () -> assertTrue(renderer.contains("## 结论")),
                () -> assertTrue(renderer.contains("## 建议动作")),
                () -> assertTrue(renderer.contains("## 数据源与缺口")),
                () -> assertTrue(renderer.contains("## 证据快照")),
                () -> assertTrue(renderer.contains(".limit(2)")),
                () -> assertTrue(renderer.contains(
                        "报告过长，已按配置截断。")),
                () -> assertFalse(renderer.contains("@Service")),
                () -> assertFalse(renderer.contains("@Component")),
                () -> assertFalse(renderer.contains("@Value")),
                () -> assertFalse(renderer.contains("@Autowired")),
                () -> assertFalse(renderer.contains("OpsAgentLlmClient")),
                () -> assertFalse(renderer.contains("OpsFinalReportTrustService")),
                () -> assertFalse(renderer.contains("executionNotes")),
                () -> assertTrue(renderer.lines().count() <= 130));
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
