package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionLifecycleOperationBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/agentdefinition/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/agentdefinition/";
    private static final String SERVICE =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsAgentDefinitionApplicationService.java";

    @Test
    void versionOperationsMustRemainInApplicationAndServiceOnlyProjectsViews() throws IOException {
        String command = read(APPLICATION + "AgentDefinitionVersionCommand.java");
        String useCase = read(APPLICATION + "AgentDefinitionLifecycleOperationUseCase.java");
        String port = read(APPLICATION + "AgentDefinitionLifecycleOperationPort.java");
        String auditPort = read(APPLICATION + "AgentDefinitionLifecycleOperationAuditPort.java");
        String adapter = read(TRIGGER + "OpsAgentDefinitionLifecycleOperationAdapter.java");
        String service = read(SERVICE);
        String operations = service.substring(
                service.indexOf("public Map<String, Object> validateVersion"),
                service.indexOf("public boolean deleteAgent"));

        assertAll(
                () -> assertTrue(command.contains("record AgentDefinitionVersionCommand")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.validate")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.publish")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.rollback")),
                () -> assertTrue(useCase.contains("operationPort.currentSnapshot")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.disable")),
                () -> assertTrue(useCase.contains("auditPort.recordTransition")),
                () -> assertTrue(useCase.contains("auditPort.recordDisable")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(port.contains("interface AgentDefinitionLifecycleOperationPort")),
                () -> assertTrue(auditPort.contains("interface AgentDefinitionLifecycleOperationAuditPort")),
                () -> assertTrue(adapter.contains("definitionGateway.list()")),
                () -> assertTrue(adapter.contains("viewMapper::view")),
                () -> assertTrue(adapter.contains("Map.of(\"disabled\", disabled)")),
                () -> assertTrue(operations.contains("lifecycleOperationUseCase.validate")),
                () -> assertTrue(operations.contains("lifecycleOperationUseCase.publish")),
                () -> assertTrue(operations.contains("lifecycleOperationUseCase.rollback")),
                () -> assertTrue(operations.contains("lifecycleOperationUseCase.disable")),
                () -> assertFalse(operations.contains("lifecycleUseCase.validate")),
                () -> assertFalse(operations.contains("lifecycleUseCase.publish")),
                () -> assertFalse(operations.contains("lifecycleUseCase.rollback")),
                () -> assertFalse(operations.contains("lifecycleUseCase.disable")),
                () -> assertFalse(operations.contains("opsConfigAuditService.record")),
                () -> assertFalse(service.contains("private int requireVersion")));
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
