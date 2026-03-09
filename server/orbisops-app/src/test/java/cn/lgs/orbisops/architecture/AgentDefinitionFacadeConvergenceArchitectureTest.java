package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionFacadeConvergenceArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/agentdefinition/";
    private static final String TRIGGER_AGENT_DEFINITION =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/agentdefinition/";
    private static final String SERVICE =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsAgentDefinitionApplicationService.java";

    @Test
    void administrationQueryAndEvalSequencesMustRemainOutsideCompatibilityFacade() throws IOException {
        String administration = read(APPLICATION + "AgentDefinitionAdministrationUseCase.java");
        String query = read(APPLICATION + "AgentDefinitionQueryUseCase.java");
        String eval = read(APPLICATION + "AgentDefinitionEvalUseCase.java");
        String assembly = read(TRIGGER_AGENT_DEFINITION + "OpsAgentDefinitionManagementAssembly.java");
        String configuration = read(TRIGGER_AGENT_DEFINITION + "OpsAgentDefinitionApplicationConfiguration.java");
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(administration.contains("administrationPort.currentSnapshot")),
                () -> assertTrue(administration.contains("administrationPort.delete")),
                () -> assertTrue(administration.contains("auditPort.recordDelete")),
                () -> assertTrue(administration.contains("administrationPort.reload")),
                () -> assertTrue(administration.contains("auditPort.recordReload")),
                () -> assertTrue(query.contains("filter(queryPort::projectScoped)")),
                () -> assertTrue(query.contains("queryPort.storedBindings")),
                () -> assertTrue(query.contains("queryPort.deriveBindings")),
                () -> assertTrue(eval.contains("evalPort.requireExistingProject")),
                () -> assertTrue(eval.contains("evalPort.createSuite")),
                () -> assertTrue(eval.contains("evalPort.run")),
                () -> assertFalse(administration.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(query.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(eval.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(service.contains("administrationUseCase.delete")),
                () -> assertTrue(service.contains("administrationUseCase.reload")),
                () -> assertTrue(service.contains("queryUseCase.listProjectAgents")),
                () -> assertTrue(service.contains("queryUseCase.bindings")),
                () -> assertTrue(service.contains("evalUseCase.createSuite")),
                () -> assertTrue(service.contains("evalUseCase.run")),
                () -> assertFalse(service.contains("private final OpsAgentDefinitionGateway")),
                () -> assertFalse(service.contains("private final AgentDefinitionLifecycleUseCase")),
                () -> assertFalse(service.contains("private final OpsConfigAuditService")),
                () -> assertFalse(service.contains("private final OpsAgentEvalAdapter")),
                () -> assertFalse(service.contains("definitionRegistry.delete(")),
                () -> assertFalse(service.contains("definitionRegistry.reload(")),
                () -> assertFalse(service.contains("agentEvalService.createSuite")),
                () -> assertFalse(service.contains("agentEvalService.run")),
                () -> assertTrue(assembly.contains("record OpsAgentDefinitionManagementAssembly")),
                () -> assertTrue(assembly.contains("public static OpsAgentDefinitionManagementAssembly create")),
                () -> assertTrue(configuration.contains("opsAgentDefinitionManagementAssembly")),
                () -> assertTrue(service.contains("OpsAgentDefinitionManagementAssembly assembly")),
                () -> assertTrue(service.contains("assembly.defaultAgentBootstrapUseCase()")),
                () -> assertFalse(service.contains("new OpsAgentDefinition")),
                () -> assertFalse(service.contains("new AgentDefinitionQueryUseCase")),
                () -> assertFalse(service.contains("new AgentDefinitionCloneUseCase")),
                () -> assertFalse(service.contains("new AgentDefinitionBindingUpdateUseCase")));
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
