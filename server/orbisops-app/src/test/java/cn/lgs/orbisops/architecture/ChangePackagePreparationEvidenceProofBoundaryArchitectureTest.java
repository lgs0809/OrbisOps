package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationEvidenceProofBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void evidenceServiceOwnsAuthoritativeStoreFilteringAndReferenceProjection() throws IOException {
        String evidence = read(TRIGGER + "OpsPreparationEvidenceService.java");

        assertAll(
                () -> assertTrue(evidence.contains("final class OpsPreparationEvidenceService")),
                () -> assertTrue(evidence.contains("Supplier<OpsEvidenceStore>")),
                () -> assertTrue(evidence.contains("store.listForRun(projectId, runId, 200)")),
                () -> assertTrue(evidence.contains("toolResultId")),
                () -> assertTrue(evidence.contains("outputHash")),
                () -> assertTrue(evidence.contains("fullOutputRef")),
                () -> assertTrue(evidence.contains("record EvidenceBundle")),
                () -> assertTrue(evidence.contains("List<Map<String, Object>> evidenceRefs")),
                () -> assertFalse(evidence.contains("UNTRUSTED_USER_INPUT")),
                () -> assertFalse(evidence.contains("ChangePackagePreparationContext")),
                () -> assertFalse(evidence.contains("org.springframework")),
                () -> assertFalse(evidence.contains("@Service")),
                () -> assertFalse(evidence.contains("interface OpsPreparationEvidence")));
    }

    @Test
    void proofFactoryOwnsFallbackTrustMarkingAndDomainContextMapping() throws IOException {
        String proof = read(TRIGGER + "OpsPreparationProofBundleFactory.java");

        assertAll(
                () -> assertTrue(proof.contains("final class OpsPreparationProofBundleFactory")),
                () -> assertTrue(proof.contains("ProofBundle create(Input input)")),
                () -> assertTrue(proof.contains("UNTRUSTED_USER_INPUT")),
                () -> assertTrue(proof.contains("preflightFallback(")),
                () -> assertTrue(proof.contains("dryRunFallback(")),
                () -> assertFalse(proof.contains("sandboxFallback(")),
                () -> assertTrue(proof.contains("new ChangePackagePreparationContext(")),
                () -> assertTrue(proof.contains("new ChangePackagePreparationProof(")),
                () -> assertTrue(proof.contains("new ChangePackagePreparationOperation(")),
                () -> assertTrue(proof.contains("notVerifiedItems(")),
                () -> assertTrue(proof.contains("repairIntent(")),
                () -> assertTrue(proof.contains("changedFiles(")),
                () -> assertFalse(proof.contains("OpsEvidenceStore")),
                () -> assertFalse(proof.contains("org.springframework")),
                () -> assertFalse(proof.contains("@Service")),
                () -> assertFalse(proof.contains("interface OpsPreparationProofBundle")));
    }

    @Test
    void preparationServiceOnlyCoordinatesEvidenceProofAndDomainDecision() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");
        String draftFactory = read(TRIGGER + "OpsPreparationPackageDraftFactory.java");
        String evidenceFactory = read(TRIGGER + "OpsPreparationEvidenceProjectionFactory.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationEvidenceService preparationEvidenceService")),
                () -> assertTrue(preparation.contains("OpsPreparationProofBundleFactory PREPARATION_PROOF_BUNDLE_FACTORY")),
                () -> assertTrue(preparation.contains("preparationEvidenceService.resolve(")),
                () -> assertTrue(preparation.contains("PREPARATION_PROOF_BUNDLE_FACTORY.create(")),
                () -> assertTrue(preparation.contains("PREPARATION_POLICY.decide(proofBundle.context())")),
                () -> assertTrue(draftFactory.contains("input.evidenceBundle().evidenceRefs()")),
                () -> assertTrue(evidenceFactory.contains("input.proofBundle().notVerifiedItems()")),
                () -> assertFalse(preparation.contains("store.listForRun(")),
                () -> assertFalse(preparation.contains("UNTRUSTED_USER_INPUT")),
                () -> assertFalse(preparation.contains("new ChangePackagePreparationContext(")),
                () -> assertFalse(preparation.contains("new ChangePackagePreparationProof(")),
                () -> assertFalse(preparation.contains("new ChangePackagePreparationOperation(")),
                () -> assertFalse(preparation.contains("private List<Map<String, Object>> trustedEvidence(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> evidenceRef(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> preflightResult(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> dryRunResult(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> sandboxResult(")),
                () -> assertFalse(preparation.contains("private boolean trustedPassed(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> untrustedUserInput(")));
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
