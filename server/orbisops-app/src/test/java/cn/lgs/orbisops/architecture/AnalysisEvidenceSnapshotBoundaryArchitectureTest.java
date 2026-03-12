package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisEvidenceSnapshotBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void reportComposerDelegatesMarkdownEvidenceSnapshotRendering() throws IOException {
        String composer = read(OPS + "OpsAnalysisReportComposer.java");
        String renderer = read(OPS + "OpsAnalysisEvidenceSnapshotRenderer.java");

        assertAll(
                () -> assertTrue(composer.contains(
                        "OpsAnalysisEvidenceSnapshotRenderer snapshotRenderer")),
                () -> assertTrue(composer.contains("return snapshotRenderer.render(response);")),
                () -> assertTrue(composer.contains("buildAiPrompt(")),
                () -> assertTrue(composer.contains("OpsFinalReportService")),
                () -> assertTrue(composer.contains("OpsAnalysisInsightService")),
                () -> assertFalse(composer.contains("## 真实数据快照")),
                () -> assertFalse(composer.contains("### 主 Agent 调查计划")),
                () -> assertFalse(composer.contains("### Agent Graph 执行节点")),
                () -> assertFalse(composer.contains("### 监控摘要")),
                () -> assertFalse(composer.contains("### 日志摘要")),
                () -> assertFalse(composer.contains("### MySQL 慢 SQL 摘要")),
                () -> assertFalse(composer.contains("### 子 Agent 调查结果")),
                () -> assertFalse(composer.contains("metricSummary(")),
                () -> assertFalse(composer.contains("sourceQueried(")),
                () -> assertFalse(composer.contains("markdownCell(")),
                () -> assertTrue(composer.lines().count() <= 130),
                () -> assertTrue(renderer.contains("String render(OpsAnalysisResponseDTO response)")),
                () -> assertTrue(renderer.contains("appendInvestigationPlan(")),
                () -> assertTrue(renderer.contains("appendExecutionNotes(")),
                () -> assertTrue(renderer.contains("appendAgentExecutionSteps(")),
                () -> assertTrue(renderer.contains("appendMetrics(")),
                () -> assertTrue(renderer.contains("appendLogs(")),
                () -> assertTrue(renderer.contains("appendSlowSql(")),
                () -> assertTrue(renderer.contains("appendInvestigationResults(")),
                () -> assertTrue(renderer.contains("appendInsights(")),
                () -> assertTrue(renderer.contains("## 真实数据快照")),
                () -> assertTrue(renderer.contains("### 接口指标 Top")),
                () -> assertTrue(renderer.contains("### MySQL 慢 SQL Top")),
                () -> assertTrue(renderer.contains(".stream().limit(8)")),
                () -> assertTrue(renderer.contains(".stream().limit(3)")),
                () -> assertTrue(renderer.contains(".stream().limit(2)")),
                () -> assertTrue(renderer.contains("value.replace(\"|\", \"\\\\|\")")),
                () -> assertFalse(renderer.contains("@Service")),
                () -> assertFalse(renderer.contains("@Component")),
                () -> assertFalse(renderer.contains("@Value")),
                () -> assertFalse(renderer.contains("@Autowired")),
                () -> assertFalse(renderer.contains("OpsFinalReportService")),
                () -> assertFalse(renderer.contains("OpsAnalysisInsightService")),
                () -> assertFalse(renderer.contains("buildAiPrompt(")),
                () -> assertTrue(renderer.lines().count() <= 330));
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
