package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceProjectionApplicationArchitectureTest {

    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/";
    private static final String TRIGGER_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsProjectWorkspaceService.java";
    private static final String TRIGGER_MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/project/OpsProjectWorkspaceProjectionMapper.java";
    private static final String AGENT_PUBLICATION_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/project/OpsProjectDefaultAgentPublicationAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/project/OpsProjectWorkspaceReadinessFailureAdapter.java";

    @Test
    void applicationOwnsTypedProjectionAndProductReadinessSemantics() throws IOException {
        String request = read(APPLICATION_ROOT + "ProjectWorkspaceProjectionRequest.java");
        String projection = read(APPLICATION_ROOT + "ProjectWorkspaceProjection.java");
        String service = read(APPLICATION_ROOT + "ProjectWorkspaceProjectionApplicationService.java");

        assertAll(
                () -> assertTrue(request.contains("record ProjectWorkspaceProjectionRequest")),
                () -> assertTrue(request.contains("record EvidenceSource")),
                () -> assertTrue(projection.contains("record ProjectWorkspaceProjection")),
                () -> assertTrue(projection.contains("record DiagnosticScenario")),
                () -> assertTrue(projection.contains("record OnboardingStep")),
                () -> assertTrue(service.contains("DEFAULT_AGENT_NOT_CONFIGURED")),
                () -> assertTrue(service.contains("DEFAULT_AGENT_NOT_PUBLISHED")),
                () -> assertTrue(service.contains("NO_RESOURCE_CONNECTED")),
                () -> assertTrue(service.contains("GENERAL_DIAGNOSIS")),
                () -> assertTrue(service.contains("CODE_INVESTIGATION")),
                () -> assertTrue(service.contains("recommendedScenarioId")),
                () -> assertTrue(service.contains("onboarding(")),
                () -> assertTrue(service.contains("ProjectDefaultAgentPublicationPort")),
                () -> assertFalse(service.contains("IAgentDefinitionRepository")),
                () -> assertFalse(service.contains("AgentDefinitionLifecycle")),
                () -> assertFalse(request.contains("Map<String, Object>")),
                () -> assertFalse(projection.contains("Map<String, Object>")),
                () -> assertFalse(service.contains("Map<String, Object>")),
                () -> assertFalse(request.contains("org.springframework")),
                () -> assertFalse(projection.contains("org.springframework")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("JdbcTemplate")));
    }

    @Test
    void triggerServiceDelegatesProjectionAndCannotReownReadinessAlgorithms() throws IOException {
        String service = read(TRIGGER_SERVICE);
        String mapper = read(TRIGGER_MAPPER);
        String assembler = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/project/OpsProjectWorkspaceViewAssembler.java");
        String publicationAdapter = read(AGENT_PUBLICATION_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);

        assertAll(
                () -> assertTrue(service.contains("ProjectWorkspaceProjectionApplicationService")),
                () -> assertTrue(service.contains("OpsProjectWorkspaceProjectionMapper")),
                () -> assertTrue(assembler.contains("projectionService.project(")),
                () -> assertTrue(assembler.contains("projectionMapper.detail(")),
                () -> assertTrue(assembler.contains("projectionMapper.catalog(")),
                () -> assertFalse(service.contains("diagnosticScenarios(")),
                () -> assertFalse(service.contains("diagnosticScenario(")),
                () -> assertFalse(service.contains("resourceReadiness(")),
                () -> assertFalse(service.contains("publishedProjectAgentExists(")),
                () -> assertFalse(service.contains("readinessReason")),
                () -> assertFalse(service.contains("GENERAL_DIAGNOSIS")),
                () -> assertFalse(service.contains("onboardingStep(")),
                () -> assertFalse(service.contains("skillReferenceView(")),
                () -> assertFalse(service.contains("knowledgeBaseView(")),
                () -> assertFalse(service.contains("resourceView(")),
                () -> assertFalse(service.contains("generatedMcpView(")),
                () -> assertFalse(service.contains("credentialView(")),
                () -> assertTrue(mapper.contains("Map<String, Object>")),
                () -> assertTrue(mapper.contains("passwordMasked")),
                () -> assertTrue(mapper.contains("safeConfig.remove(\"runtimeEnv\")")),
                () -> assertTrue(publicationAdapter.contains("implements ProjectDefaultAgentPublicationPort")),
                () -> assertTrue(publicationAdapter.contains("IAgentDefinitionRepository")),
                () -> assertTrue(publicationAdapter.contains("AgentDefinitionLifecycle.PUBLISHED")),
                () -> assertTrue(failureAdapter.contains("检查项目{}就绪状态失败，按未接入处理")));
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
