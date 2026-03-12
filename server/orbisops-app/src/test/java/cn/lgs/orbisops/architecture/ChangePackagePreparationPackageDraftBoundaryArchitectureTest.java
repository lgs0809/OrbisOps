package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationPackageDraftBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void boundedPlainFactoriesOwnEvidenceContextLandingAndAuditProjections() throws IOException {
        String draft = read(TRIGGER + "OpsPreparationPackageDraftFactory.java");
        String evidence = read(TRIGGER + "OpsPreparationEvidenceProjectionFactory.java");
        String context = read(TRIGGER + "OpsPreparationContextProjectionFactory.java");
        String landing = read(TRIGGER + "OpsPreparationLandingPlanProjectionFactory.java");
        String audit = read(TRIGGER + "OpsPreparationAuditPayloadFactory.java");

        assertAll(
                () -> assertTrue(draft.contains("final class OpsPreparationPackageDraftFactory")),
                () -> assertTrue(draft.contains("EVIDENCE_PROJECTION_FACTORY.create(")),
                () -> assertTrue(draft.contains("LANDING_PLAN_PROJECTION_FACTORY.create(")),
                () -> assertTrue(draft.contains("CONTEXT_PROJECTION_FACTORY.packageContext(")),
                () -> assertTrue(draft.contains("CONTEXT_PROJECTION_FACTORY.skillRefValues(")),
                () -> assertTrue(draft.contains("packageRequest.put(\"evidence\"")),
                () -> assertTrue(draft.contains("packageRequest.put(\"landingPlan\"")),
                () -> assertTrue(draft.contains("packageRequest.put(\"preparationMethodRef\"")),
                () -> assertFalse(draft.contains("private Map<String, Object> evidence(")),
                () -> assertFalse(draft.contains("private Map<String, Object> landingPlan(")),
                () -> assertFalse(draft.contains("applyContextBundle(")),
                () -> assertFalse(draft.contains("riskDisclosure(")),
                () -> assertTrue(evidence.contains("final class OpsPreparationEvidenceProjectionFactory")),
                () -> assertTrue(evidence.contains("evidence.put(\"trustedEvidence\"")),
                () -> assertTrue(evidence.contains("evidence.put(\"riskDisclosure\"")),
                () -> assertTrue(evidence.contains("CONTEXT_PROJECTION_FACTORY.evidenceContext(")),
                () -> assertTrue(context.contains("Map<String, Object> packageContext(")),
                () -> assertTrue(context.contains("Map<String, Object> evidenceContext(")),
                () -> assertTrue(context.contains("List<Object> skillRefValues(")),
                () -> assertTrue(landing.contains("plan.put(\"replanTriggers\"")),
                () -> assertTrue(audit.contains("REQUEST_RISK_LOWER_THAN_SYSTEM_ASSESSMENT")),
                () -> assertTrue(audit.contains("record AuditPayload")),
                () -> assertNoFrameworkDependency(draft),
                () -> assertNoFrameworkDependency(evidence),
                () -> assertNoFrameworkDependency(context),
                () -> assertNoFrameworkDependency(landing),
                () -> assertNoFrameworkDependency(audit));
    }

    @Test
    void preparationServiceOnlyOrchestratesCollaboratorsDecisionAndAuditDispatch() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationPackageDraftFactory PREPARATION_PACKAGE_DRAFT_FACTORY")),
                () -> assertTrue(preparation.contains("OpsPreparationAuditPayloadFactory PREPARATION_AUDIT_PAYLOAD_FACTORY")),
                () -> assertTrue(preparation.contains("PREPARATION_PACKAGE_DRAFT_FACTORY.create(")),
                () -> assertTrue(preparation.contains("PREPARATION_AUDIT_PAYLOAD_FACTORY.riskEscalated(")),
                () -> assertTrue(preparation.contains("PREPARATION_POLICY.decide(")),
                () -> assertTrue(preparation.contains("audit(")),
                () -> assertTrue(preparation.contains("projectId,")),
                () -> assertTrue(preparation.contains("contextBundleService.requireForRequest(")),
                () -> assertTrue(preparation.contains("PREPARATION_METHOD_REFERENCE_FACTORY.create(")),
                () -> assertFalse(preparation.contains("evidence.put(")),
                () -> assertFalse(preparation.contains("landingPlan.put(")),
                () -> assertFalse(preparation.contains("packageRequest.put(")),
                () -> assertFalse(preparation.contains("REQUEST_RISK_LOWER_THAN_SYSTEM_ASSESSMENT")),
                () -> assertFalse(preparation.contains("riskDisclosure(")),
                () -> assertFalse(preparation.contains("summaryFor(")),
                () -> assertFalse(preparation.contains("usedSkillRefs")),
                () -> assertFalse(preparation.contains("DEFAULT_ROLLBACK_PLAN")),
                () -> assertFalse(preparation.contains("DEFAULT_VERIFICATION_CRITERIA")));
    }

    private void assertNoFrameworkDependency(String source) {
        assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("ObjectProvider"));
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
