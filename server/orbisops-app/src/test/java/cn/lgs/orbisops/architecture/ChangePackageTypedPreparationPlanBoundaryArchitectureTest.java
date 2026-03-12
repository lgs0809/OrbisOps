package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageTypedPreparationPlanBoundaryArchitectureTest {

    @Test
    void preparationPortMustReturnTypedIdentityEnvelopes() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackagePreparationPlanPort.java");
        String useCase = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/PrepareChangePackageUseCase.java");
        String snapshot = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/changepackage/model/ChangePackageSnapshot.java");
        String service = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/change/OpsChangePackagePreparationService.java");
        String controller = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/http/admin/OpsChangePackageAdminController.java");

        assertAll(
                () -> assertTrue(port.contains("ChangePackagePreparationPlan prepare(")),
                () -> assertTrue(port.contains("ChangePackagePreparationPlan prepareForSession(")),
                () -> assertTrue(port.contains("ChangePackageRevisionPlan revise(")),
                () -> assertFalse(port.contains("defaultPreparationAgent(")),
                () -> assertFalse(port.contains("ChangePackagePreparationAgentSelection")),
                () -> assertFalse(port.contains("Map<String, Object> prepare(")),
                () -> assertFalse(port.contains("Map<String, Object> revise(")),
                () -> assertTrue(useCase.contains("requiredPlan.projectId()")),
                () -> assertTrue(useCase.contains("requiredPlan.requestedPackageId()")),
                () -> assertTrue(useCase.contains("requiredRevision.changes()")),
                () -> assertTrue(useCase.contains("requiredRevision.changeSummary()")),
                () -> assertTrue(snapshot.contains("public ChangePackageStatus status()")),
                () -> assertTrue(snapshot.contains("public ChangePackageType packageType()")),
                () -> assertTrue(snapshot.contains("public String preparationAgentId()")),
                () -> assertTrue(useCase.contains("snapshot.status()")),
                () -> assertTrue(useCase.contains("snapshot.packageType()")),
                () -> assertFalse(useCase.contains("values.get(\"status\")")),
                () -> assertFalse(useCase.contains("values.get(\"packageType\")")),
                () -> assertFalse(useCase.contains("plan.get(\"projectId\")")),
                () -> assertFalse(useCase.contains("revision.get(\"changeSummary\")")),
                () -> assertTrue(service.contains("ChangePackagePreparationPlan.from(buildPackageRequest(")),
                () -> assertTrue(service.contains("ChangePackageRevisionPlan.from(")),
                () -> assertFalse(controller.contains("default-preparation-agent")),
                () -> assertFalse(controller.contains("defaultPreparationAgent(")));
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
