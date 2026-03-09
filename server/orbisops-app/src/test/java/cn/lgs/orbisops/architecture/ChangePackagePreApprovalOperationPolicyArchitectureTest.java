package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreApprovalOperationPolicyArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void operationShapeRiskAndValidationSafetyBelongToDomainPolicy() throws IOException {
        String service = read(TRIGGER + "OpsPreApprovalValidationService.java");
        String mapper = read(TRIGGER + "OpsPreApprovalOperationMapper.java");
        String mcpExecutor = read(TRIGGER + "OpsPreApprovalMcpValidationExecutor.java");
        String operation = read(DOMAIN + "model/ChangePackageValidationOperation.java");
        String assessment = read(DOMAIN + "model/ChangePackageValidationOperationAssessment.java");
        String policy = read(DOMAIN + "service/ChangePackagePreApprovalValidationPolicy.java");

        assertAll(
                () -> assertTrue(service.contains("ChangePackagePreApprovalValidationPolicy VALIDATION_POLICY")),
                () -> assertTrue(service.contains("OpsPreApprovalOperationMapper OPERATION_MAPPER")),
                () -> assertTrue(service.contains("VALIDATION_POLICY.assess(")),
                () -> assertTrue(mcpExecutor.contains("VALIDATION_POLICY.validationExecutable(")),
                () -> assertTrue(service.contains("ChangePackageValidationOperationAssessment assessment")),
                () -> assertFalse(service.contains("private void requireOperation(")),
                () -> assertFalse(service.contains("private boolean highRisk(")),
                () -> assertFalse(service.contains("private String maxRisk(")),
                () -> assertFalse(service.contains("private int score(")),
                () -> assertFalse(service.contains("private String normalizeEffectType(")),
                () -> assertFalse(service.contains("TARGET_WRITE_REQUIRES_CHANGE_PACKAGE:")),
                () -> assertFalse(service.contains("POLICY_STALE_OR_UNKNOWN:")),
                () -> assertTrue(mapper.contains("new ChangePackageValidationOperation(")),
                () -> assertTrue(mapper.contains("presentFields")),
                () -> assertTrue(mapper.contains("raw.get(\"mcpId\"), raw.get(\"toolsetId\")")),
                () -> assertTrue(operation.contains("missingRequiredFields()")),
                () -> assertTrue(operation.contains("policyUnknown()")),
                () -> assertTrue(operation.contains("ChangePackagePreparationOperation.normalizeEffectType")),
                () -> assertTrue(assessment.contains("trustedProofRequired")),
                () -> assertTrue(policy.contains("TARGET_WRITE_REQUIRES_CHANGE_PACKAGE:")),
                () -> assertTrue(policy.contains("POLICY_STALE_OR_UNKNOWN:")),
                () -> assertTrue(policy.contains("validationExecutable(")),
                () -> assertTrue(policy.contains("VALIDATION_EFFECTS")),
                () -> assertTrue(policy.contains("TARGET_SCOPES")),
                () -> assertDomainPlain(operation),
                () -> assertDomainPlain(assessment),
                () -> assertDomainPlain(policy),
                () -> assertTriggerPlain(mapper));
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
