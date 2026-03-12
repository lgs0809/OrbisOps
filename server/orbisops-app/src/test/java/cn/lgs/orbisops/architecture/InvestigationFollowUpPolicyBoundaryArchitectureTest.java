package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationFollowUpPolicyBoundaryArchitectureTest {

    private static final String TRIGGER_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/investigation/";

    @Test
    void deterministicRoutingRulesBelongToDomainPolicyBehindObservationRouterAndDtoAcl() throws IOException {
        String executor = read(TRIGGER_OPS + "OpsInvestigationExecutor.java");
        String loop = read(TRIGGER_OPS + "OpsInvestigationLoopService.java");
        String routing = read(TRIGGER_OPS + "OpsInvestigationObservationRoutingService.java");
        String acl = read(TRIGGER_OPS + "OpsInvestigationFollowUpService.java");
        String policy = read(DOMAIN + "service/InvestigationFollowUpPolicy.java");

        assertAll(
                () -> assertTrue(executor.contains("OpsInvestigationLoopService loopService")),
                () -> assertTrue(loop.contains("OpsInvestigationObservationRoutingService observationRoutingService")),
                () -> assertTrue(loop.contains("observationRoutingService.decide(")),
                () -> assertFalse(loop.contains("followUpService.decide(")),
                () -> assertTrue(routing.contains("OpsInvestigationFollowUpService followUpService")),
                () -> assertTrue(routing.contains("deterministicDecision(")),
                () -> assertTrue(routing.contains("followUpService.decide(")),
                () -> assertTrue(routing.contains("followUpService.shouldStopAfterKnowledgeObservation(")),
                () -> assertFalse(routing.contains("enqueueConditionalTasks(")),
                () -> assertFalse(routing.contains("enqueueFallbackTasks(")),
                () -> assertFalse(routing.contains("shouldActivateConditionalTask(")),
                () -> assertFalse(routing.contains("hasAnomalySignal(")),
                () -> assertFalse(routing.contains("hasDatabaseLatencySignal(")),
                () -> assertFalse(routing.contains("pureKnowledgeQuestion(")),
                () -> assertFalse(routing.contains("runtime evidence contains db latency")),
                () -> assertFalse(routing.contains("@Service")),
                () -> assertTrue(acl.contains("InvestigationFollowUpPolicy POLICY")),
                () -> assertTrue(acl.contains("InvestigationQuestionSignals")),
                () -> assertTrue(acl.contains("InvestigationObservation")),
                () -> assertTrue(acl.contains("InvestigationFollowUpTask")),
                () -> assertFalse(acl.contains("@Service")),
                () -> assertFalse(acl.contains("JSONObject")),
                () -> assertTrue(policy.contains("public InvestigationFollowUpDecision decide(")),
                () -> assertTrue(policy.contains("pureKnowledgeQuestion(")),
                () -> assertTrue(policy.contains("hasAnomalySignal(")),
                () -> assertTrue(policy.contains("hasDatabaseLatencySignal(")),
                () -> assertTrue(policy.contains("SOURCE_MYSQL_SLOW_SQL")),
                () -> assertFalse(policy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(policy.contains("OpsQuestionContext")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")));
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
