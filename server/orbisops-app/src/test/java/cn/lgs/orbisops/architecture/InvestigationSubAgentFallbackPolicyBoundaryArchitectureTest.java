package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationSubAgentFallbackPolicyBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/investigation/";

    @Test
    void deterministicSubAgentFallbackBelongsToDomainBehindDtoAcl() throws IOException {
        String facade = read(OPS + "OpsSubAgentDecisionService.java");
        String acl = read(OPS + "OpsSubAgentFallbackDecisionService.java");
        String policy = read(DOMAIN + "service/InvestigationSubAgentFallbackPolicy.java");
        String queryModel = read(DOMAIN + "model/InvestigationSubAgentQueryDecision.java");
        String reviewModel = read(DOMAIN + "model/InvestigationSubAgentReviewDecision.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsSubAgentFallbackDecisionService fallbackDecisionService")),
                () -> assertTrue(facade.contains("fallbackDecisionService.query(")),
                () -> assertTrue(facade.contains("fallbackDecisionService.review(")),
                () -> assertFalse(facade.contains("String retrievalMode =")),
                () -> assertFalse(facade.contains("new OpsSubAgentDecision(")),
                () -> assertFalse(facade.contains("new OpsAgentReview(")),
                () -> assertFalse(facade.contains("只读兜底策略：")),
                () -> assertFalse(facade.contains("真实查询结果")),
                () -> assertFalse(facade.contains("LLM 子 Agent 复盘不可用：")),
                () -> assertFalse(facade.contains("已保留真实查询 observation")),
                () -> assertTrue(acl.contains("InvestigationSubAgentFallbackPolicy POLICY")),
                () -> assertTrue(acl.contains("OpsAgentRunRequestDTO")),
                () -> assertTrue(acl.contains("OpsQuestionContext")),
                () -> assertTrue(acl.contains("InvestigationSubAgentQueryDecision")),
                () -> assertTrue(acl.contains("InvestigationSubAgentReviewDecision")),
                () -> assertFalse(acl.contains("@Service")),
                () -> assertFalse(acl.contains("@Value")),
                () -> assertFalse(acl.contains("org.springframework")),
                () -> assertFalse(acl.contains("com.alibaba.fastjson")),
                () -> assertTrue(acl.lines().count() <= 80),
                () -> assertTrue(policy.contains("public InvestigationSubAgentQueryDecision query(")),
                () -> assertTrue(policy.contains("public InvestigationSubAgentReviewDecision review(")),
                () -> assertTrue(policy.contains("只读兜底策略：")),
                () -> assertTrue(policy.contains("真实查询结果")),
                () -> assertTrue(policy.contains("LLM 子 Agent 复盘不可用：")),
                () -> assertTrue(policy.contains("已保留真实查询 observation")),
                () -> assertFalse(policy.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(policy.contains("OpsQuestionContext")),
                () -> assertFalse(policy.contains("OpsSubAgentDecision")),
                () -> assertFalse(policy.contains("OpsAgentReview")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertTrue(policy.lines().count() <= 100),
                () -> assertFalse(queryModel.contains("cn.lgs.orbisops.api")),
                () -> assertFalse(reviewModel.contains("cn.lgs.orbisops.api")));
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
