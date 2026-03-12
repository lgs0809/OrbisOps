package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisAiPromptBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void reportComposerDelegatesAiPromptMaterializationToPlainBuilder() throws IOException {
        String composer = read(OPS + "OpsAnalysisReportComposer.java");
        String builder = read(OPS + "OpsAnalysisAiPromptBuilder.java");

        assertAll(
                () -> assertTrue(composer.contains(
                        "OpsAnalysisAiPromptBuilder promptBuilder")),
                () -> assertTrue(composer.contains(
                        "return promptBuilder.build(response);")),
                () -> assertTrue(composer.contains(
                        "OpsAnalysisEvidenceSnapshotRenderer snapshotRenderer")),
                () -> assertTrue(composer.contains(
                        "OpsAnalysisInsightService insightService")),
                () -> assertTrue(composer.contains("OpsFinalReportService")),
                () -> assertTrue(composer.contains("response.setInsights(")),
                () -> assertTrue(composer.contains("response.setMarkdownReport(")),
                () -> assertTrue(composer.contains("response.setAiPrompt(")),
                () -> assertFalse(composer.contains("StringBuilder")),
                () -> assertFalse(composer.contains(
                        "你是生产业务系统的运维主 Agent")),
                () -> assertFalse(composer.contains("### 最近日志样本")),
                () -> assertFalse(composer.contains("### MySQL 慢 SQL 样本")),
                () -> assertFalse(composer.contains("请输出：")),
                () -> assertFalse(composer.contains("recentLogs(")),
                () -> assertFalse(composer.contains("slowSqlSamples(")),
                () -> assertFalse(composer.contains("abbreviate(")),
                () -> assertTrue(composer.lines().count() <= 90),
                () -> assertTrue(builder.contains(
                        "String build(OpsAnalysisResponseDTO response)")),
                () -> assertTrue(builder.contains("StringBuilder prompt")),
                () -> assertTrue(builder.contains(
                        "你是生产业务系统的运维主 Agent")),
                () -> assertTrue(builder.contains(
                        "不要把采集数据原样罗列成流水账")),
                () -> assertTrue(builder.contains(
                        "不要假装查过未选择的数据源")),
                () -> assertTrue(builder.contains(
                        "prompt.append(response.getMarkdownReport()).append")),
                () -> assertTrue(builder.contains("### 最近日志样本")),
                () -> assertTrue(builder.contains("### MySQL 慢 SQL 样本")),
                () -> assertTrue(builder.contains("recentLogs(response)")),
                () -> assertTrue(builder.contains("slowSqlSamples(response)")),
                () -> assertTrue(builder.contains(
                        "abbreviate(sample.getSqlText(), 240)")),
                () -> assertTrue(builder.contains("1. 运行状态结论。")),
                () -> assertTrue(builder.contains("2. 关键数据解读。")),
                () -> assertTrue(builder.contains("3. 异常或风险判断。")),
                () -> assertTrue(builder.contains("4. 具体排查或优化建议。")),
                () -> assertTrue(builder.contains(
                        "5. 本次分析使用了哪些真实数据源。")),
                () -> assertFalse(builder.contains("@Service")),
                () -> assertFalse(builder.contains("@Component")),
                () -> assertFalse(builder.contains("@Value")),
                () -> assertFalse(builder.contains("@Autowired")),
                () -> assertFalse(builder.contains("OpsFinalReportService")),
                () -> assertFalse(builder.contains("OpsAnalysisInsightService")),
                () -> assertFalse(builder.contains(
                        "OpsAnalysisEvidenceSnapshotRenderer")),
                () -> assertTrue(builder.lines().count() <= 90));
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
