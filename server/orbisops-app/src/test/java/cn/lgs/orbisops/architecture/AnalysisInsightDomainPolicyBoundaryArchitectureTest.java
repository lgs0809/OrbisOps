package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisInsightDomainPolicyBoundaryArchitectureTest {

    private static final String TRIGGER_OPS =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN_MODEL =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/investigation/model/";
    private static final String DOMAIN_SERVICE =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/investigation/service/";

    @Test
    void composerDelegatesInsightRulesThroughDtoAclToPureDomainPolicy()
            throws IOException {
        String composer = read(TRIGGER_OPS + "OpsAnalysisReportComposer.java");
        String acl = read(TRIGGER_OPS + "OpsAnalysisInsightService.java");
        String insight = read(DOMAIN_MODEL + "InvestigationInsight.java");
        String policy = read(DOMAIN_SERVICE + "InvestigationInsightPolicy.java");

        assertAll(
                () -> assertTrue(composer.contains(
                        "OpsAnalysisInsightService insightService")),
                () -> assertTrue(composer.contains(
                        "return insightService.build(response);")),
                () -> assertTrue(composer.contains("buildDataSnapshot(")),
                () -> assertTrue(composer.contains("buildAiPrompt(")),
                () -> assertFalse(composer.contains("hasExecutedResult(")),
                () -> assertFalse(composer.contains("normalizeSource(")),
                () -> assertFalse(composer.contains("InsightDTO insight(")),
                () -> assertFalse(composer.contains("isUnavailable(")),
                () -> assertFalse(composer.contains("getErrorRate() > 5D")),
                () -> assertFalse(composer.contains(
                        "getHeapMemoryUsagePercent() > 80D")),
                () -> assertFalse(composer.contains("发现 MySQL 慢 SQL")),
                () -> assertFalse(composer.contains("实时数据已由子 Agent 查询")),
                () -> assertTrue(composer.lines().count() <= 320),
                () -> assertTrue(acl.contains("InvestigationInsightPolicy POLICY")),
                () -> assertTrue(acl.contains("POLICY.assess(")),
                () -> assertTrue(acl.contains("OpsAnalysisResponseDTO")),
                () -> assertTrue(acl.contains("InvestigationInsight")),
                () -> assertTrue(acl.contains("normalizeSource(")),
                () -> assertTrue(acl.contains("case \"es\" -> \"elasticsearch\"")),
                () -> assertTrue(acl.contains("\"BLOCKED\", \"ERROR\"")),
                () -> assertFalse(acl.contains("@Service")),
                () -> assertFalse(acl.contains("@Component")),
                () -> assertFalse(acl.contains("@Value")),
                () -> assertFalse(acl.contains("@Autowired")),
                () -> assertFalse(acl.contains("OpsFinalReportService")),
                () -> assertFalse(acl.contains("StringBuilder")),
                () -> assertTrue(acl.lines().count() <= 170),
                () -> assertTrue(insight.contains("public record InvestigationInsight(")),
                () -> assertFalse(insight.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(insight.contains("org.springframework")),
                () -> assertTrue(policy.contains(
                        "public final class InvestigationInsightPolicy")),
                () -> assertTrue(policy.contains("List<InvestigationInsight> assess(")),
                () -> assertTrue(policy.contains("record Source(")),
                () -> assertTrue(policy.contains("record Metrics(")),
                () -> assertTrue(policy.contains("record Logs(")),
                () -> assertTrue(policy.contains("record SlowSql(")),
                () -> assertTrue(policy.contains("record Input(")),
                () -> assertTrue(policy.contains("metrics.errorRate() > 5D")),
                () -> assertTrue(policy.contains(
                        "metrics.heapMemoryUsagePercent() > 80D")),
                () -> assertTrue(policy.contains("slowSql.maxQueryTimeMs() > 3000D")),
                () -> assertTrue(policy.contains("发现 MySQL 慢 SQL")),
                () -> assertTrue(policy.contains("实时数据已由子 Agent 查询")),
                () -> assertFalse(policy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertTrue(policy.lines().count() <= 230));
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
