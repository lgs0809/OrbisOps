package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationPlanEnvelopeBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void plainFactoryOwnsTheCompletePlanEnvelope() throws IOException {
        String factory = read(TRIGGER + "OpsPreparationPlanEnvelopeFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("final class OpsPreparationPlanEnvelopeFactory")),
                () -> assertTrue(factory.contains("Envelope create(Input input)")),
                () -> assertTrue(factory.contains("candidatePlans(")),
                () -> assertTrue(factory.contains("approvalBoundary(")),
                () -> assertTrue(factory.contains("approvedEffectTypes(")),
                () -> assertTrue(factory.contains("approvedEffectScopes(")),
                () -> assertTrue(factory.contains("preferredPlan(")),
                () -> assertTrue(factory.contains("adjustmentPolicy(")),
                () -> assertTrue(factory.contains("allowedLandingAdjustments(")),
                () -> assertTrue(factory.contains("allowedToolsFromSteps(")),
                () -> assertTrue(factory.contains("REPLAN_TRIGGERS")),
                () -> assertTrue(factory.contains("ChangePackagePreparationOperation.normalizeEffectType(")),
                () -> assertTrue(factory.contains("record Input")),
                () -> assertTrue(factory.contains("record Envelope")),
                () -> assertFalse(factory.contains("org.springframework")),
                () -> assertFalse(factory.contains("@Service")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertFalse(factory.contains("interface OpsPreparationPlanEnvelope")));
    }

    @Test
    void preparationServiceConsumesEnvelopeWithoutDuplicatingPlanRules() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");
        String draftFactory = read(TRIGGER + "OpsPreparationPackageDraftFactory.java");
        String evidenceFactory = read(TRIGGER + "OpsPreparationEvidenceProjectionFactory.java");
        String landingFactory = read(TRIGGER + "OpsPreparationLandingPlanProjectionFactory.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationPlanEnvelopeFactory PREPARATION_PLAN_ENVELOPE_FACTORY")),
                () -> assertTrue(preparation.contains("PREPARATION_PLAN_ENVELOPE_FACTORY.create(")),
                () -> assertTrue(evidenceFactory.contains("planEnvelope().candidatePlans()")),
                () -> assertTrue(draftFactory.contains("planEnvelope().approvalBoundary()")),
                () -> assertTrue(draftFactory.contains("planEnvelope().preferredPlan()")),
                () -> assertTrue(draftFactory.contains("planEnvelope().adjustmentPolicy()")),
                () -> assertTrue(draftFactory.contains("planEnvelope().allowedLandingAdjustments()")),
                () -> assertTrue(draftFactory.contains("planEnvelope().allowedTools()")),
                () -> assertTrue(landingFactory.contains("envelope.replanTriggers()")),
                () -> assertFalse(preparation.contains("private List<Map<String, Object>> candidatePlans(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> approvalBoundary(")),
                () -> assertFalse(preparation.contains("approvedEffectTypes(")),
                () -> assertFalse(preparation.contains("approvedEffectScopes(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> preferredPlan(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> adjustmentPolicy(")),
                () -> assertFalse(preparation.contains("private List<Map<String, Object>> allowedLandingAdjustments(")),
                () -> assertFalse(preparation.contains("allowedToolsFromSteps(")),
                () -> assertFalse(preparation.contains("normalizeRisk(")),
                () -> assertFalse(preparation.contains("maxRisk(")),
                () -> assertFalse(preparation.contains("riskRank(")));
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
