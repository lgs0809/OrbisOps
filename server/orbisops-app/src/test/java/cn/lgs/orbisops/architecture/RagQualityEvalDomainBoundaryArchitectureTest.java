package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQualityEvalDomainBoundaryArchitectureTest {

    private static final String DOMAIN =
            "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/rageval/";
    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/rag/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/rag/";

    @Test
    void qualityAlgorithmsMustRemainInDomainAndOnlineRetrievalBehindApplicationPort() throws IOException {
        String policy = read(DOMAIN + "service/RagQualityAssessmentPolicy.java");
        String assessment = read(DOMAIN + "model/RagEvalProbeAssessment.java");
        String useCase = read(APPLICATION + "RagQualityProbeUseCase.java");
        String runUseCase = read(APPLICATION + "RagQualityRunUseCase.java");
        String caseCatalogUseCase = read(APPLICATION + "RagQualityCaseCatalogUseCase.java");
        String caseCatalogPort = read(APPLICATION + "RagQualityCaseCatalogPort.java");
        String port = read(APPLICATION + "RagQualityRetrievalPort.java");
        String adapter = read(TRIGGER + "OpsRagQualityRetrievalAdapter.java");
        String persistenceAdapter = read(TRIGGER + "OpsRagQualityRunPersistenceAdapter.java");
        String caseCatalogAdapter = read(TRIGGER + "OpsRagQualityCaseCatalogAdapter.java");
        String assembly = read(TRIGGER + "OpsRagQualityEvalManagementAssembly.java");
        String configuration = read(TRIGGER + "RagQualityEvalConfiguration.java");
        String service = read(TRIGGER + "RagQualityEvalService.java");

        assertAll(
                () -> assertTrue(policy.contains("PASS_COVERAGE_THRESHOLD = 0.6D")),
                () -> assertTrue(policy.contains("scoreContent")),
                () -> assertTrue(policy.contains("reciprocalRank")),
                () -> assertTrue(policy.contains("RagEvalRunAssessment aggregate")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.application")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("Map<String, Object>")),
                () -> assertTrue(assessment.contains("record RagEvalProbeAssessment")),
                () -> assertTrue(port.contains("interface RagQualityRetrievalPort")),
                () -> assertTrue(useCase.contains("retrievalPort.retrieve")),
                () -> assertTrue(useCase.contains("assessmentPolicy.assessProbe")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(runUseCase.contains("probeUseCase.probe")),
                () -> assertTrue(runUseCase.contains("assessmentPolicy.aggregate")),
                () -> assertTrue(runUseCase.contains("persistencePort.save")),
                () -> assertFalse(runUseCase.contains("org.springframework")),
                () -> assertFalse(runUseCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(runUseCase.contains("Map<String, Object>")),
                () -> assertTrue(caseCatalogPort.contains("interface RagQualityCaseCatalogPort")),
                () -> assertTrue(caseCatalogUseCase.contains("catalogPort.ensureReady")),
                () -> assertTrue(caseCatalogUseCase.contains("catalogPort.list")),
                () -> assertTrue(caseCatalogUseCase.contains("catalogPort.save")),
                () -> assertTrue(caseCatalogUseCase.contains("catalogPort.delete")),
                () -> assertTrue(caseCatalogUseCase.contains("catalogPort.listEnabled")),
                () -> assertFalse(caseCatalogUseCase.contains("Map<String, Object>")),
                () -> assertFalse(caseCatalogUseCase.contains("com.alibaba.fastjson")),
                () -> assertFalse(caseCatalogUseCase.contains("org.springframework")),
                () -> assertFalse(caseCatalogUseCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(adapter.contains("implements RagQualityRetrievalPort")),
                () -> assertTrue(adapter.contains("new RagAnswerAdvisor")),
                () -> assertTrue(adapter.contains("qa_filter_expression")),
                () -> assertTrue(persistenceAdapter.contains("implements RagQualityRunPersistencePort")),
                () -> assertTrue(persistenceAdapter.contains("repository.saveRun")),
                () -> assertTrue(caseCatalogAdapter.contains("implements RagQualityCaseCatalogPort")),
                () -> assertTrue(caseCatalogAdapter.contains("repository.listCases")),
                () -> assertTrue(caseCatalogAdapter.contains("repository.saveCase")),
                () -> assertTrue(caseCatalogAdapter.contains("repository.deleteCase")),
                () -> assertTrue(caseCatalogAdapter.contains("repository.listEnabledCases")),
                () -> assertTrue(caseCatalogAdapter.contains("JSON.toJSONString")),
                () -> assertTrue(configuration.contains("opsRagQualityEvalManagementAssembly")),
                () -> assertTrue(configuration.contains("OpsRagQualityEvalManagementAssembly.create")),
                () -> assertTrue(assembly.contains("new OpsRagQualityRetrievalAdapter")),
                () -> assertTrue(assembly.contains("new OpsRagQualityCaseCatalogAdapter")),
                () -> assertTrue(assembly.contains("new OpsRagQualityRunPersistenceAdapter")),
                () -> assertTrue(assembly.contains("new RagQualityProbeUseCase")),
                () -> assertTrue(assembly.contains("new RagQualityRunUseCase")),
                () -> assertTrue(service.contains("RagQualityEvalService(OpsRagQualityEvalManagementAssembly assembly)")),
                () -> assertTrue(service.contains("probeUseCase.probe")),
                () -> assertTrue(service.contains("RagQualityProbeCommand command")),
                () -> assertTrue(service.contains("runUseCase.run")),
                () -> assertTrue(service.contains("RagQualityRunCommand command")),
                () -> assertTrue(service.contains("caseCatalogUseCase.list")),
                () -> assertTrue(service.contains("caseCatalogUseCase.save")),
                () -> assertTrue(service.contains("caseCatalogUseCase.delete")),
                () -> assertTrue(service.contains("caseCatalogUseCase.enabledCases")),
                () -> assertFalse(service.contains("ObjectProvider<")),
                () -> assertFalse(service.contains("IRagEvalRepository")),
                () -> assertFalse(service.contains("IRagKnowledgeRepository")),
                () -> assertFalse(service.contains("new OpsRagQuality")),
                () -> assertFalse(service.contains("new RagQualityProbeUseCase")),
                () -> assertFalse(service.contains("new RagQualityRunUseCase")),
                () -> assertFalse(service.contains("ragEvalRepository.")),
                () -> assertFalse(service.contains(".listCases(enabled")),
                () -> assertFalse(service.contains(".saveCase(id")),
                () -> assertFalse(service.contains(".deleteCase(id")),
                () -> assertFalse(service.contains(".listEnabledCases")),
                () -> assertFalse(service.contains(".ensureTables")),
                () -> assertFalse(service.contains("normalizeEvalCaseRow")),
                () -> assertFalse(service.contains("for (Map<String, Object> evalCase")),
                () -> assertFalse(service.contains("assessmentFromProbe")),
                () -> assertFalse(service.contains("persistEvalRun")),
                () -> assertFalse(service.contains("ragEvalRepository.saveRun")),
                () -> assertFalse(service.contains("private double scoreContent")),
                () -> assertFalse(service.contains("private double reciprocalRank")),
                () -> assertFalse(service.contains("private String recommendation")),
                () -> assertFalse(service.contains("private List<Document> retrieveWithOnlineAdvisor")),
                () -> assertFalse(service.contains("new RagAnswerAdvisor")),
                () -> assertFalse(service.contains("coverage >= 0.6D")));
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
