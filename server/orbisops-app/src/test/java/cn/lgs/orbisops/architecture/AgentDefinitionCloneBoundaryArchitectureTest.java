package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionCloneBoundaryArchitectureTest {

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
    void cloneSequenceMustRemainInApplicationAndMutableProjectionInAdapter() throws IOException {
        String command = read(APPLICATION + "AgentDefinitionCloneCommand.java");
        String useCase = read(APPLICATION + "AgentDefinitionCloneUseCase.java");
        String port = read(APPLICATION + "AgentDefinitionClonePort.java");
        String auditPort = read(APPLICATION + "AgentDefinitionCloneAuditPort.java");
        String adapter = read(TRIGGER + "OpsAgentDefinitionCloneAdapter.java");
        String service = read(LEGACY_SERVICE);

        assertAll(
                () -> assertTrue(command.contains("record AgentDefinitionCloneCommand")),
                () -> assertTrue(useCase.contains("public final class AgentDefinitionCloneUseCase")),
                () -> assertTrue(useCase.contains("clonePort.requireExistingProject")),
                () -> assertTrue(useCase.contains("clonePort.resolveSource")),
                () -> assertTrue(useCase.contains("clonePort.sanitizeForProject")),
                () -> assertTrue(useCase.contains("clonePort.prepareClone")),
                () -> assertTrue(useCase.contains("clonePort.normalizeExecutionShape")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.saveDraft")),
                () -> assertTrue(useCase.contains("auditPort.recordClone")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(port.contains("interface AgentDefinitionClonePort")),
                () -> assertTrue(auditPort.contains("interface AgentDefinitionCloneAuditPort")),
                () -> assertTrue(adapter.contains("implements AgentDefinitionClonePort")),
                () -> assertTrue(adapter.contains("AgentDefinitionCloneAuditPort")),
                () -> assertTrue(adapter.contains("definition.setVersion(null)")),
                () -> assertTrue(adapter.contains("definition.setLifecycle(\"DRAFT\")")),
                () -> assertTrue(adapter.contains("definition.setSource(\"UI\")")),
                () -> assertTrue(service.contains("cloneUseCase.clone")),
                () -> assertFalse(service.contains("clone.setAgentId")),
                () -> assertFalse(service.contains("clone.setProjectId")),
                () -> assertFalse(service.contains("clone.setVersion(null)")),
                () -> assertFalse(service.contains("capabilityApplicationService.sanitizeForProject(clone")),
                () -> assertFalse(service.contains("\"clone\", saved.getAgentId()")));
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
