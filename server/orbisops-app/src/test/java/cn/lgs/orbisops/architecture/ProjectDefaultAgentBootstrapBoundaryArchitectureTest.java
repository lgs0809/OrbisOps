package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDefaultAgentBootstrapBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/agentdefinition/";
    private static final String DOMAIN =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/agentdefinition/service/";
    private static final String TRIGGER_DEFINITION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/agentdefinition/";
    private static final String LEGACY_SERVICE =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsAgentDefinitionApplicationService.java";

    @Test
    void bootstrapProcessMustRemainInApplicationAndTriggerMustOnlyAdapt() throws IOException {
        String useCase = read(APPLICATION + "ProjectDefaultAgentBootstrapUseCase.java");
        String definitionPort = read(APPLICATION + "ProjectDefaultAgentDefinitionPort.java");
        String evalPort = read(APPLICATION + "ProjectDefaultAgentEvalPort.java");
        String auditPort = read(APPLICATION + "ProjectDefaultAgentAuditPort.java");
        String suiteFactory = read(APPLICATION + "ProjectDefaultAgentEvalSuiteFactory.java");
        String microkernel = read(DOMAIN + "AgentMicrokernelPolicy.java");
        String definitionAdapter = read(TRIGGER_DEFINITION
                + "OpsProjectDefaultAgentDefinitionAdapter.java");
        String auditAdapter = read(TRIGGER_DEFINITION
                + "OpsProjectDefaultAgentAuditAdapter.java");
        String evalAdapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/agenteval/OpsAgentEvalAdapter.java");
        String service = read(LEGACY_SERVICE);

        assertAll(
                () -> assertTrue(useCase.contains("public final class ProjectDefaultAgentBootstrapUseCase")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.saveDraft")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.validate")),
                () -> assertTrue(useCase.contains("evalPort.createReleaseSuite")),
                () -> assertTrue(useCase.contains("evalPort.runReleaseEvaluation")),
                () -> assertTrue(useCase.contains("evalPort.assertReleaseAllowed")),
                () -> assertTrue(useCase.contains("lifecycleUseCase.publish")),
                () -> assertTrue(useCase.contains("auditPort.record")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(definitionPort.contains("interface ProjectDefaultAgentDefinitionPort")),
                () -> assertTrue(evalPort.contains("interface ProjectDefaultAgentEvalPort")),
                () -> assertTrue(auditPort.contains("interface ProjectDefaultAgentAuditPort")),
                () -> assertTrue(suiteFactory.contains("AgentEvalCreateSuiteCommand")),
                () -> assertFalse(suiteFactory.contains("Map.of(\"cases\"")),
                () -> assertTrue(microkernel.contains("OpsBuiltinSubAgentRole.MAIN_ASSISTANT")),
                () -> assertFalse(microkernel.contains("OpsAgentDefinition")),
                () -> assertTrue(definitionAdapter.contains("implements ProjectDefaultAgentDefinitionPort")),
                () -> assertTrue(auditAdapter.contains("implements ProjectDefaultAgentAuditPort")),
                () -> assertTrue(evalAdapter.contains("implements ProjectDefaultAgentEvalPort")),
                () -> assertTrue(service.contains("defaultAgentBootstrapUseCase.ensure")),
                () -> assertFalse(service.contains("private Map<String, Object> defaultAgentEvalSuite")),
                () -> assertFalse(service.contains("private boolean usesBuiltInMicrokernel")),
                () -> assertFalse(service.contains("agentEvalService.assertReleaseAllowed(validated)")));
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
