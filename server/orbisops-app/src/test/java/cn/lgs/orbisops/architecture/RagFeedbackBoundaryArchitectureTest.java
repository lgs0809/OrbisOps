package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFeedbackBoundaryArchitectureTest {

    private static final String APPLICATION_RAG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/rag/";
    private static final String TRIGGER_RAG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/rag/";

    @Test
    void feedbackGapAndEvalPromotionMustBelongToTypedApplicationBoundary() throws IOException {
        String command = read(APPLICATION_RAG + "RagFeedbackSubmitCommand.java");
        String port = read(APPLICATION_RAG + "RagFeedbackCatalogPort.java");
        String evalPort = read(APPLICATION_RAG + "RagFeedbackEvalCasePort.java");
        String useCase = read(APPLICATION_RAG + "RagFeedbackUseCase.java");
        String catalogAdapter = read(TRIGGER_RAG + "OpsRagFeedbackCatalogAdapter.java");
        String evalAdapter = read(TRIGGER_RAG + "OpsRagFeedbackEvalCaseAdapter.java");
        String assembly = read(TRIGGER_RAG + "OpsRagFeedbackManagementAssembly.java");
        String configuration = read(TRIGGER_RAG + "RagFeedbackConfiguration.java");
        String service = read(TRIGGER_RAG + "RagFeedbackService.java");

        assertAll(
                () -> assertTrue(command.contains("record RagFeedbackSubmitCommand")),
                () -> assertTrue(command.contains("throw new IllegalArgumentException(\"query 不能为空\")")),
                () -> assertTrue(port.contains("Long insertFeedback(RagFeedbackSubmitCommand command)")),
                () -> assertTrue(port.contains("RagKnowledgeGap upsertGap")),
                () -> assertTrue(port.contains("RagKnowledgeGap findGap")),
                () -> assertTrue(evalPort.contains("RagQualityCaseRecord save")),
                () -> assertTrue(useCase.contains("catalogPort.insertFeedback(command)")),
                () -> assertTrue(useCase.contains("Boolean.FALSE.equals(command.useful())")),
                () -> assertTrue(useCase.contains("catalogPort.upsertGap")),
                () -> assertTrue(useCase.contains("evalCasePort.save(new RagQualityCaseSaveCommand")),
                () -> assertTrue(useCase.contains("updateGapStatus(id, TRIAGED_STATUS)")),
                () -> assertTrue(useCase.indexOf("evalCasePort.save") < useCase.indexOf("updateGapStatus(id, TRIAGED_STATUS)")),
                () -> assertTrue(useCase.contains("Math.max(1, Math.min(limit, MAX_LIMIT))")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertFalse(useCase.contains("com.alibaba.fastjson")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("IRagFeedbackRepository")),
                () -> assertTrue(catalogAdapter.contains("implements RagFeedbackCatalogPort")),
                () -> assertTrue(catalogAdapter.contains("private final IRagFeedbackRepository repository")),
                () -> assertTrue(catalogAdapter.contains("JSON.toJSONString(command.chunkIds())")),
                () -> assertTrue(catalogAdapter.contains("repository.queryGap(id)")),
                () -> assertTrue(evalAdapter.contains("implements RagFeedbackEvalCasePort")),
                () -> assertTrue(evalAdapter.contains("caseCatalogUseCase.save(command)")),
                () -> assertTrue(assembly.contains("new RagFeedbackUseCase")),
                () -> assertTrue(assembly.contains("new OpsRagFeedbackCatalogAdapter(repository)")),
                () -> assertTrue(assembly.contains("qualityAssembly.caseCatalogUseCase()")),
                () -> assertTrue(assembly.contains("resolvedSettings.autoInit()")),
                () -> assertTrue(configuration.contains("OpsRagFeedbackManagementAssembly opsRagFeedbackManagementAssembly")),
                () -> assertTrue(configuration.contains("orbisops.rag.feedback.auto-init")),
                () -> assertTrue(service.contains("private final RagFeedbackUseCase useCase")),
                () -> assertTrue(service.contains("private final OpsRagFeedbackViewMapper viewMapper")),
                () -> assertTrue(service.contains("RagFeedbackService(OpsRagFeedbackManagementAssembly assembly)")),
                () -> assertTrue(service.contains("useCase.promoteGapToEvalCase(id)")),
                () -> assertFalse(service.contains("IRagFeedbackRepository")),
                () -> assertFalse(service.contains("RagQualityEvalService")),
                () -> assertFalse(service.contains("RagFeedbackSettings")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("upsertGap(")),
                () -> assertFalse(service.contains("updateGapStatus(id, \"TRIAGED\")")));
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
