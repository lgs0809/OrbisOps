package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionBindingUpdateBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/agentdefinition/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/agentdefinition/";
    private static final String LEGACY_SERVICE =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsAgentDefinitionApplicationService.java";

    @Test
    void updateSequenceMustRemainInApplicationAndCompatibilityMapsInTrigger() throws IOException {
        String command = read(APPLICATION + "AgentDefinitionBindingUpdateCommand.java");
        String useCase = read(APPLICATION + "AgentDefinitionBindingUpdateUseCase.java");
        String port = read(APPLICATION + "AgentDefinitionBindingUpdatePort.java");
        String auditPort = read(APPLICATION + "AgentDefinitionBindingUpdateAuditPort.java");
        String result = read(APPLICATION + "AgentDefinitionBindingUpdateResult.java");
        String adapter = read(TRIGGER + "OpsAgentDefinitionBindingUpdateAdapter.java");
        String service = read(LEGACY_SERVICE);
        String updateMethod = service.substring(
                service.indexOf("public Map<String, Object> updateAgentBindings"),
                service.indexOf("public Map<String, Object> cloneAgent"));

        assertAll(
                () -> assertTrue(command.contains("record AgentDefinitionBindingUpdateCommand")),
                () -> assertTrue(useCase.contains("public final class AgentDefinitionBindingUpdateUseCase")),
                () -> assertTrue(useCase.contains("updatePort.resolveDraft")),
                () -> assertTrue(useCase.contains("updatePort.applyBindings")),
                () -> assertTrue(useCase.contains("updatePort.assertProjectAndBindingsValid")),
                () -> assertTrue(useCase.contains("updatePort.normalizeExecutionShape")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.saveDraft")),
                () -> assertTrue(useCase.contains("updatePort.effectiveBindings")),
                () -> assertTrue(useCase.contains("auditPort.recordBindingUpdate")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(port.contains("interface AgentDefinitionBindingUpdatePort")),
                () -> assertTrue(auditPort.contains("interface AgentDefinitionBindingUpdateAuditPort")),
                () -> assertTrue(result.contains("record AgentDefinitionBindingUpdateResult")),
                () -> assertTrue(adapter.contains("implements AgentDefinitionBindingUpdatePort")),
                () -> assertTrue(adapter.contains("AgentDefinitionBindingUpdateAuditPort")),
                () -> assertTrue(adapter.contains("capabilities.applyBindings")),
                () -> assertTrue(adapter.contains("capabilities.assertValid")),
                () -> assertTrue(adapter.contains("definitionGateway.listCapabilityBindings")),
                () -> assertTrue(updateMethod.contains("bindingUpdateUseCase.update")),
                () -> assertTrue(updateMethod.contains("capabilityApplicationService.requestBindings(request)")),
                () -> assertFalse(updateMethod.contains("definitionRegistry.resolve(")),
                () -> assertFalse(updateMethod.contains("capabilityApplicationService.applyBindings(")),
                () -> assertFalse(updateMethod.contains("capabilityApplicationService.assertValid(")),
                () -> assertFalse(updateMethod.contains("\"update-bindings\"")));
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
