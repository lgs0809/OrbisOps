package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionDraftSaveBoundaryArchitectureTest {

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
    void draftRulesMustRemainInApplicationAndServiceOnlySelectAuditAction() throws IOException {
        String useCase = read(APPLICATION + "AgentDefinitionDraftSaveUseCase.java");
        String port = read(APPLICATION + "AgentDefinitionDraftSavePort.java");
        String auditPort = read(APPLICATION + "AgentDefinitionDraftSaveAuditPort.java");
        String adapter = read(TRIGGER + "OpsAgentDefinitionDraftSaveAdapter.java");
        String service = read(SERVICE);
        String saveMethods = service.substring(
                service.indexOf("public Map<String, Object> saveAgent"),
                service.indexOf("public List<Map<String, Object>> versions"));

        assertAll(
                () -> assertTrue(useCase.contains("draftPort.assertProjectAndBindingsValid")),
                () -> assertTrue(useCase.contains("draftPort.normalizeExecutionShape")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.saveDraft")),
                () -> assertTrue(useCase.contains("auditPort.recordDraftSave")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(port.contains("interface AgentDefinitionDraftSavePort")),
                () -> assertTrue(auditPort.contains("interface AgentDefinitionDraftSaveAuditPort")),
                () -> assertTrue(adapter.contains("capabilities.requireExistingProject")),
                () -> assertTrue(adapter.contains("capabilities.assertValid")),
                () -> assertTrue(adapter.contains("executionShapeMapper.normalize")),
                () -> assertTrue(saveMethods.contains("draftSaveUseCase.save")),
                () -> assertTrue(saveMethods.contains("save-draft-compat")),
                () -> assertTrue(saveMethods.contains("save-draft")),
                () -> assertFalse(saveMethods.contains("capabilityApplicationService.assertValid")),
                () -> assertFalse(saveMethods.contains("lifecycleUseCase.saveDraft")),
                () -> assertFalse(saveMethods.contains("opsConfigAuditService.record")),
                () -> assertFalse(service.contains("private void requireProject")));
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
