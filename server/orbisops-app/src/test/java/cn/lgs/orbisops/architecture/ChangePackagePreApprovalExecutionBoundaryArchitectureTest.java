package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreApprovalExecutionBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void validationEntryDelegatesRepairAndMcpSideEffectsToBoundedExecutors() throws IOException {
        String service = read(TRIGGER + "OpsPreApprovalValidationService.java");
        String repair = read(TRIGGER + "OpsPreApprovalRepairValidationExecutor.java");
        String mcp = read(TRIGGER + "OpsPreApprovalMcpValidationExecutor.java");
        String result = read(TRIGGER + "OpsPreApprovalValidationExecutionResult.java");

        assertAll(
                () -> assertTrue(service.contains("OpsPreApprovalRepairValidationExecutor repairValidationExecutor")),
                () -> assertTrue(service.contains("OpsPreApprovalMcpValidationExecutor mcpValidationExecutor")),
                () -> assertTrue(service.contains("repairValidationExecutor.validate(")),
                () -> assertTrue(service.contains("mcpValidationExecutor.obtainProof(")),
                () -> assertTrue(service.contains("private void merge(")),
                () -> assertFalse(service.contains("code_bash")),
                () -> assertFalse(service.contains("executeControlledTest(")),
                () -> assertFalse(service.contains("executeMcpValidation(")),
                () -> assertFalse(service.contains("executeMcp(")),
                () -> assertFalse(service.contains("OpsMcpServerConfig")),
                () -> assertFalse(service.contains("JSON.toJSONString(")),
                () -> assertFalse(service.contains("verifyRepairWorkspace(")),
                () -> assertFalse(service.contains("CONTROLLED_BASH_EXECUTED")),
                () -> assertFalse(service.contains("MCP_VALIDATION_EXECUTOR_UNAVAILABLE")),
                () -> assertTrue(repair.contains("code_bash")),
                () -> assertTrue(repair.contains("OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW")),
                () -> assertTrue(repair.contains("verifyRepairWorkspace(")),
                () -> assertTrue(repair.contains("CONTROLLED_BASH_EXECUTED")),
                () -> assertTrue(mcp.contains("OpsMcpServerConfig")),
                () -> assertTrue(mcp.contains("JSON.toJSONString(")),
                () -> assertTrue(mcp.contains("executeMcp(")),
                () -> assertTrue(mcp.contains("MCP_VALIDATION_EXECUTOR_UNAVAILABLE")),
                () -> assertTrue(mcp.contains("TRUSTED_PROOF_TYPES")),
                () -> assertTrue(result.contains("record OpsPreApprovalValidationExecutionResult")),
                () -> assertPlain(repair),
                () -> assertPlain(mcp),
                () -> assertPlain(result));
    }

    private void assertPlain(String source) {
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("ObjectProvider"));
        assertFalse(source.contains("org.springframework"));
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
