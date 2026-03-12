package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalReportTrustDomainPolicyBoundaryArchitectureTest {

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
    void finalReportDelegatesEvidenceTrustThroughDtoAclToPureDomainPolicy()
            throws IOException {
        String finalReport = read(TRIGGER_OPS + "OpsFinalReportService.java");
        String acl = read(TRIGGER_OPS + "OpsFinalReportTrustService.java");
        String decision = read(
                DOMAIN_MODEL + "InvestigationFinalReportTrustDecision.java");
        String policy = read(
                DOMAIN_SERVICE + "InvestigationFinalReportTrustPolicy.java");

        assertAll(
                () -> assertTrue(finalReport.contains(
                        "OpsFinalReportTrustService trustService")),
                () -> assertTrue(finalReport.contains(
                        "trustService.trusted(response, markdownReport)")),
                () -> assertTrue(finalReport.contains(
                        "OpsFinalReportLlmProtocolService llmProtocol")),
                () -> assertTrue(finalReport.contains("buildRuleReport(")),
                () -> assertTrue(finalReport.contains("rejectReport(")),
                () -> assertFalse(finalReport.contains("@Slf4j")),
                () -> assertFalse(finalReport.contains("log.warn(")),
                () -> assertFalse(finalReport.contains("isTrustedLlmReport(")),
                () -> assertFalse(finalReport.contains("executedSources(")),
                () -> assertFalse(finalReport.contains("sourceAliases(")),
                () -> assertFalse(finalReport.contains("claimsQueriedEvidence(")),
                () -> assertFalse(finalReport.contains("canonicalSource(")),
                () -> assertFalse(finalReport.contains("hasInvestigationGaps(")),
                () -> assertFalse(finalReport.contains("LinkedHashSet")),
                () -> assertFalse(finalReport.contains("Locale")),
                () -> assertTrue(finalReport.lines().count() <= 240),
                () -> assertTrue(acl.contains(
                        "InvestigationFinalReportTrustPolicy POLICY")),
                () -> assertTrue(acl.contains("POLICY.assess(")),
                () -> assertTrue(acl.contains("OpsAnalysisResponseDTO")),
                () -> assertTrue(acl.contains(
                        "InvestigationFinalReportTrustPolicy.Evidence")),
                () -> assertTrue(acl.contains(
                        "InvestigationFinalReportTrustPolicy.Result")),
                () -> assertTrue(acl.contains(
                        "最终报告缺少数据源与缺口章节")),
                () -> assertTrue(acl.contains(
                        "最终报告声称使用了未执行的数据源")),
                () -> assertTrue(acl.contains("最终报告未呈现调查缺口")),
                () -> assertFalse(acl.contains("@Service")),
                () -> assertFalse(acl.contains("@Component")),
                () -> assertFalse(acl.contains("@Value")),
                () -> assertFalse(acl.contains("@Autowired")),
                () -> assertFalse(acl.contains("OpsAgentLlmClient")),
                () -> assertFalse(acl.contains("JSONObject")),
                () -> assertTrue(acl.lines().count() <= 90),
                () -> assertTrue(decision.contains(
                        "public record InvestigationFinalReportTrustDecision(")),
                () -> assertTrue(decision.contains(
                        "MISSING_SOURCE_OR_GAP_SECTION")),
                () -> assertTrue(decision.contains(
                        "UNEXECUTED_SOURCE_CLAIM")),
                () -> assertTrue(decision.contains(
                        "MISSING_INVESTIGATION_GAP")),
                () -> assertFalse(decision.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(decision.contains("org.springframework")),
                () -> assertFalse(decision.contains("com.alibaba.fastjson")),
                () -> assertFalse(decision.contains("lombok")),
                () -> assertTrue(decision.lines().count() <= 70),
                () -> assertTrue(policy.contains(
                        "public final class InvestigationFinalReportTrustPolicy")),
                () -> assertTrue(policy.contains(
                        "InvestigationFinalReportTrustDecision assess(Input input)")),
                () -> assertTrue(policy.contains("record Evidence(")),
                () -> assertTrue(policy.contains("record Result(")),
                () -> assertTrue(policy.contains("record Input(")),
                () -> assertTrue(policy.contains("sourceAliases()")),
                () -> assertTrue(policy.contains("claimsQueriedEvidence(")),
                () -> assertTrue(policy.contains("canonicalSource(")),
                () -> assertTrue(policy.contains(
                        "Set.of(\"FOUND\", \"SUCCEEDED\", \"SUCCESS\", \"OK\")")),
                () -> assertFalse(policy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("lombok")),
                () -> assertTrue(policy.lines().count() <= 200));
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
