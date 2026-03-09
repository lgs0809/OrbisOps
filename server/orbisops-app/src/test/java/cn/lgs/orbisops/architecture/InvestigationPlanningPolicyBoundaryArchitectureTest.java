package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationPlanningPolicyBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/investigation/";

    @Test
    void deterministicPlanningRulesBelongToDomainBehindDtoAcl() throws IOException {
        String planner = read(OPS + "OpsMainAgentPlanner.java");
        String acl = read(OPS + "OpsMainAgentDeterministicPlanningService.java");
        String policy = read(DOMAIN + "service/InvestigationPlanningPolicy.java");
        String signals = read(DOMAIN + "service/InvestigationPlanningSignalPolicy.java");

        assertAll(
                () -> assertTrue(planner.contains("OpsMainAgentDeterministicPlanningService deterministicPlanningService")),
                () -> assertTrue(planner.contains("deterministicPlanningService.initial(")),
                () -> assertTrue(planner.contains("deterministicPlanningService.replan(")),
                () -> assertTrue(planner.contains("deterministicPlanningService.guardrails(")),
                () -> assertTrue(planner.contains("deterministicPlanningService.allSources(")),
                () -> assertFalse(planner.contains("rulePlan(")),
                () -> assertFalse(planner.contains("ruleReplan(")),
                () -> assertFalse(planner.contains("applyPlannerGuardrails(")),
                () -> assertFalse(planner.contains("hasInsufficientEvidence(")),
                () -> assertFalse(planner.contains("hasDatabaseLatencySignal(")),
                () -> assertFalse(planner.contains("问题包含 MySQL 慢查询、索引或数据库耗时线索")),
                () -> assertTrue(acl.contains("InvestigationPlanningPolicy POLICY")),
                () -> assertTrue(acl.contains("InvestigationPlanningSignals")),
                () -> assertTrue(acl.contains("InvestigationPlanningObservation")),
                () -> assertTrue(acl.contains("OpsQuestionContext")),
                () -> assertTrue(acl.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(acl.contains("@Service")),
                () -> assertFalse(acl.contains("@Value")),
                () -> assertFalse(acl.contains("JSONObject")),
                () -> assertTrue(policy.contains("public InvestigationPlanningPlan initialPlan(")),
                () -> assertTrue(policy.contains("public InvestigationPlanningPlan replan(")),
                () -> assertTrue(policy.contains("public InvestigationPlanningPlan applyGuardrails(")),
                () -> assertTrue(policy.contains("SOURCE_MYSQL_SLOW_SQL")),
                () -> assertTrue(policy.lines().count() <= 350),
                () -> assertFalse(policy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(policy.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(policy.contains("OpsQuestionContext")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertTrue(signals.contains("pureKnowledgeQuestion(")),
                () -> assertTrue(signals.contains("hasStrongLogSignal(")),
                () -> assertFalse(signals.contains("OpsQuestionContext")),
                () -> assertFalse(signals.contains("org.springframework")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
