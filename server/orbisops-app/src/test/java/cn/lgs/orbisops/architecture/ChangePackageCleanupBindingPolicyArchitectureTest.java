package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageCleanupBindingPolicyArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/service/ChangePackageCleanupPolicy.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/ChangePackageCleanupUseCase.java";

    @Test
    void domainPolicyOwnsWorkspaceBindingPrecedence() throws IOException {
        String policy = read(POLICY);

        int direct = policy.indexOf("ChangePackageCurrentField.REPAIR_WORKSPACE_ID");
        int cleanupPlan = policy.indexOf("ChangePackageCurrentField.CLEANUP_PLAN_JSON");
        int evidence = policy.indexOf("ChangePackageCurrentField.EVIDENCE_JSON");

        assertAll(
                () -> assertTrue(policy.contains("public String boundWorkspaceId(ChangePackageCurrent current)")),
                () -> assertTrue(direct >= 0 && cleanupPlan > direct && evidence > cleanupPlan),
                () -> assertTrue(policy.contains("cleanupPlan.get(\"repairWorkspaceId\")")),
                () -> assertTrue(policy.contains("cleanupPlan.get(\"workspaceId\")")),
                () -> assertTrue(policy.contains("evidence.get(\"repairWorkspaceId\")")),
                () -> assertTrue(policy.contains("evidence.get(\"workspaceId\")")),
                () -> assertTrue(policy.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertFalse(policy.contains("org.springframework")));
    }

    @Test
    void applicationDelegatesBindingRuleWithoutJsonParsing() throws IOException {
        String application = read(APPLICATION);

        assertAll(
                () -> assertTrue(application.contains("CLEANUP_POLICY.boundWorkspaceId(current)")),
                () -> assertFalse(application.contains("private String boundWorkspaceId")),
                () -> assertFalse(application.contains("private Map<String, Object> object")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("JSON.parse")),
                () -> assertFalse(application.contains("TypeReference")));
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
