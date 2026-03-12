package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageCommandBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void stableApprovalValidationReviewAndCleanupFactsUseNamedBoundaries() throws IOException {
        String commands = read(APPLICATION + "ChangePackageCommands.java");
        String approval = read(APPLICATION + "ChangePackageApprovalUseCase.java");
        String validation = read(APPLICATION + "ChangePackageValidationWritebackUseCase.java");
        String cleanup = read(APPLICATION + "ChangePackageCleanupUseCase.java");
        String assessment = read(DOMAIN + "service/ChangePackageApprovalAssessmentFactory.java");
        String actorContext = read(DOMAIN + "model/ChangePackageApprovalActorContext.java");
        String controlTools = read(TRIGGER + "ops/runtime/OpsChangePackageControlToolProvider.java");
        String adminController = read(TRIGGER + "http/admin/OpsChangePackageAdminController.java");
        String userController = read(TRIGGER + "http/agent/OpsUserChangePackageController.java");

        assertAll(
                () -> assertTrue(commands.contains("ChangePackageValidationReport validationReport")),
                () -> assertTrue(commands.contains("ChangePackageReviewRequest request")),
                () -> assertTrue(commands.contains("ChangePackageApprovalContext approvalContext")),
                () -> assertTrue(commands.contains("ChangePackageRejectionRequest request")),
                () -> assertTrue(commands.contains("ChangePackageCleanupRequest request")),
                () -> assertTrue(actorContext.contains("record ChangePackageApprovalActorContext(")),
                () -> assertTrue(assessment.contains("ChangePackageApprovalActorContext approvalContext")),
                () -> assertFalse(assessment.contains("approvalContext.get(")),
                () -> assertFalse(approval.contains("command.request().get(")),
                () -> assertFalse(approval.contains("command.approvalContext().get(")),
                () -> assertFalse(cleanup.contains("command.request().get(\"repairWorkspaceId\")")),
                () -> assertFalse(validation.contains("command.validationReport().get(")),
                () -> assertFalse(validation.contains("report.remove(\"_validationExecutionToken\")")),
                () -> assertTrue(validation.contains("validationReport.executionToken()")),
                () -> assertTrue(controlTools.contains("values.put(\"actorScope\", principal.scope())")),
                () -> assertTrue(controlTools.contains("permissionService.assertCanApprovePackage(projectId, principal)")),
                () -> assertFalse(controlTools.contains("principal.serviceToken()")),
                () -> assertTrue(adminController.contains("ChangePackageApprovalContext approvalContext(")),
                () -> assertFalse(adminController.contains("principal.serviceToken()")),
                () -> assertTrue(userController.contains("ChangePackageApprovalContext approvalContext(")),
                () -> assertFalse(userController.contains("principal.serviceToken()")));
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
