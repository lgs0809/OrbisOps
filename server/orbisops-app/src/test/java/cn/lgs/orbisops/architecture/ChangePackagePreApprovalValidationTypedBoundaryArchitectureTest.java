package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreApprovalValidationTypedBoundaryArchitectureTest {

    @Test
    void validationPortMustExposeAuthoritativeTypedOutcome() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackageValidationPort.java");
        String useCase = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ValidateChangePackageUseCase.java");
        String outcome = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackagePreApprovalValidationOutcome.java");
        String service = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/change/OpsPreApprovalValidationService.java");

        assertAll(
                () -> assertTrue(port.contains("ChangePackagePreApprovalValidationOutcome validate(")),
                () -> assertFalse(port.contains("Map<String, Object> validate(")),
                () -> assertTrue(useCase.contains("validateOutcome(command).packageView()")),
                () -> assertTrue(outcome.contains("ChangePackageValidationOutcome writebackOutcome")),
                () -> assertTrue(outcome.contains("ChangePackageValidationReport report")),
                () -> assertTrue(outcome.contains("Map<String, Object> packageView")),
                () -> assertTrue(service.contains("ChangePackageValidationOutcome writebackOutcome")),
                () -> assertTrue(service.contains("ChangePackageValidationReport.from(signedReport)")),
                () -> assertTrue(service.contains("new ChangePackagePreApprovalValidationOutcome(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
