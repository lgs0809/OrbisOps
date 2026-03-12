package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageApprovalProofBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/";
    private static final String CHANGE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";
    private static final String APPROVAL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/changepackage/OpsChangePackageApprovalSupportAdapter.java";

    @Test
    void approvalVerificationUsesTypedDomainAndBoundedExternalAdapters() throws IOException {
        String verifier = read(CHANGE + "OpsChangePackageProofVerifier.java");
        String snapshot = read(CHANGE + "OpsChangePackageApprovalSnapshotReader.java");
        String mapper = read(CHANGE + "OpsChangePackageApprovalOperationMapper.java");
        String proof = read(CHANGE + "OpsChangePackageApprovalProofService.java");
        String repair = read(CHANGE + "OpsChangePackageRepairApprovalVerifier.java");
        String operation = read(DOMAIN + "model/ChangePackageApprovalOperation.java");
        String assessment = read(DOMAIN + "model/ChangePackageApprovalOperationAssessment.java");
        String policy = read(DOMAIN + "service/ChangePackageApprovalOperationPolicy.java");
        String adapter = read(APPROVAL_ADAPTER);

        assertAll(
                () -> assertTrue(verifier.contains("ChangePackageCurrent current")),
                () -> assertTrue(verifier.contains("ChangePackageVersion targetVersion")),
                () -> assertTrue(verifier.contains("current.packageType()")),
                () -> assertTrue(verifier.contains("current.state().value(ChangePackageCurrentField.RISK_LEVEL)")),
                () -> assertTrue(verifier.contains("OPERATION_POLICY.verify(")),
                () -> assertTrue(verifier.contains("repairApprovalVerifier.verify(")),
                () -> assertFalse(verifier.contains("@Service")),
                () -> assertFalse(verifier.contains("ObjectProvider")),
                () -> assertFalse(verifier.contains("com.alibaba.fastjson")),
                () -> assertFalse(verifier.contains("Map<String, Object> current")),
                () -> assertFalse(verifier.contains("verifyTrustedProof(")),
                () -> assertFalse(verifier.contains("computeRepairDiff(")),
                () -> assertTrue(snapshot.contains("OpsPreApprovalStructuredValueReader")),
                () -> assertTrue(snapshot.contains("landingPlanJson")),
                () -> assertTrue(snapshot.contains("preferredPlan")),
                () -> assertTrue(mapper.contains("new ChangePackageApprovalOperation(")),
                () -> assertTrue(mapper.contains("raw.get(\"toolsetId\"), raw.get(\"mcpId\")")),
                () -> assertTrue(proof.contains("current.projectId()")),
                () -> assertTrue(proof.contains("version.packageId()")),
                () -> assertTrue(proof.contains("version.packageHash()")),
                () -> assertFalse(proof.contains("snapshot.get(\"packageId\")")),
                () -> assertTrue(repair.contains("current.projectId()")),
                () -> assertTrue(repair.contains("computeRepairDiff(")),
                () -> assertTrue(operation.contains("boolean preconditionsPresent")),
                () -> assertTrue(assessment.contains("trustedProofRequired")),
                () -> assertTrue(policy.contains("ChangePackageCanonicalHasher.calculateOperationHashes")),
                () -> assertTrue(policy.contains("生产写 operation 必须显式要求 ChangePackage 和审批")),
                () -> assertTrue(adapter.contains("proofVerifier().verifyPackageBeforeApprove(current, version)")),
                () -> assertTrue(adapter.contains("repairWorkspaceServiceProvider.getIfAvailable()")),
                () -> assertTrue(adapter.contains("trustedProofServiceProvider.getIfAvailable()")),
                () -> assertFalse(adapter.contains("versionView(")),
                () -> assertFalse(adapter.contains("ObjectProvider<OpsChangePackageProofVerifier>")),
                () -> assertDomainPlain(operation),
                () -> assertDomainPlain(assessment),
                () -> assertDomainPlain(policy),
                () -> assertTriggerPlain(snapshot),
                () -> assertTriggerPlain(mapper),
                () -> assertTriggerPlain(proof),
                () -> assertTriggerPlain(repair));
    }

    private void assertDomainPlain(String source) {
        assertFalse(source.contains("cn.lgs.orbisops.trigger"));
        assertFalse(source.contains("cn.lgs.orbisops.infrastructure"));
        assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("com.alibaba.fastjson"));
    }

    private void assertTriggerPlain(String source) {
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("ObjectProvider"));
        assertFalse(source.contains("org.springframework"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
