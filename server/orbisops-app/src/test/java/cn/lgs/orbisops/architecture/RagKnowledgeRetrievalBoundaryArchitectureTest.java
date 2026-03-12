package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagKnowledgeRetrievalBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String RAG_CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/rag/";
    private static final String DOMAIN_RETRIEVAL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/retrieval/";

    @Test
    void ragSubAgentDelegatesSettingsScopePolicyAndRetrievalToNarrowBoundaries() throws IOException {
        String subAgent = read(OPS + "RagKnowledgeOpsSubAgent.java");
        String retrieval = read(OPS + "OpsRagKnowledgeRetrievalService.java");
        String settings = read(OPS + "OpsRagKnowledgeSettings.java");
        String resources = read(OPS + "OpsRagKnowledgeRuntimeResources.java");
        String policy = read(OPS + "OpsRagKnowledgePolicy.java");
        String domainPolicy = read(
                DOMAIN_RETRIEVAL + "service/KnowledgeRetrievalPolicy.java");
        String scopeResolver = read(OPS + "OpsProjectKnowledgeScopeResolver.java");
        String configuration = read(RAG_CONFIGURATION + "OpsRagKnowledgeConfiguration.java");

        assertAll(
                () -> assertTrue(subAgent.contains("OpsRagKnowledgeRetrievalService retrievalService")),
                () -> assertTrue(subAgent.contains("retrievalService.bm25Available()")),
                () -> assertFalse(subAgent.contains("IRagKnowledgeRepository")),
                () -> assertFalse(subAgent.contains("new OpsRagKnowledgeRetrievalService(ragKnowledgeRepository)")),
                () -> assertTrue(subAgent.contains("OpsRagKnowledgeSettings settings")),
                () -> assertTrue(subAgent.contains("OpsProjectKnowledgeScopeResolver projectKnowledgeScopeResolver")),
                () -> assertTrue(subAgent.contains("OpsRagKnowledgeRuntimeResources resources")),
                () -> assertTrue(subAgent.contains("OpsRagKnowledgePolicy policy")),
                () -> assertTrue(subAgent.contains("retrievalService.retrieve(")),
                () -> assertTrue(subAgent.contains("settings.retrievalSettings()")),
                () -> assertTrue(subAgent.contains("policy.retrievalModeForIteration(")),
                () -> assertTrue(subAgent.contains("policy.buildQuery(")),
                () -> assertTrue(subAgent.contains("projectKnowledgeScopeResolver.resolveKnowledgeBaseId(")),
                () -> assertTrue(subAgent.contains("projectKnowledgeScopeResolver.filterFor(")),
                () -> assertTrue(subAgent.contains("resources.vectorStoreAvailable()")),
                () -> assertTrue(subAgent.contains("resources.multimodalEmbeddingService()")),
                () -> assertFalse(subAgent.contains("@Autowired(required = false)")),
                () -> assertFalse(subAgent.contains("org.springframework.ai.embedding")),
                () -> assertFalse(subAgent.contains("org.springframework.ai.vectorstore")),
                () -> assertFalse(subAgent.contains("@Value")),
                () -> assertFalse(subAgent.contains("ProjectDefinitionApplicationService")),
                () -> assertFalse(subAgent.contains("LinkedHashSet")),
                () -> assertFalse(subAgent.contains("private String retrievalModeForIteration(")),
                () -> assertFalse(subAgent.contains("private String buildRagQuery(")),
                () -> assertFalse(subAgent.contains("private String projectKnowledgeBaseId(")),
                () -> assertFalse(subAgent.contains("private OpsRagKnowledgeRetrievalService.Settings ragSettings()")),
                () -> assertFalse(subAgent.contains("RagAnswerAdvisor")),
                () -> assertFalse(subAgent.contains("SearchRequest")),
                () -> assertFalse(subAgent.contains("AiClientAdvisorVO")),
                () -> assertTrue(subAgent.lines().count() <= 330),

                () -> assertTrue(settings.contains("public record OpsRagKnowledgeSettings(")),
                () -> assertTrue(settings.contains("OpsRagKnowledgeRetrievalService.Settings retrievalSettings()")),
                () -> assertTrue(settings.contains("public static OpsRagKnowledgeSettings defaults()")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("@Value")),

                () -> assertTrue(resources.contains("public record OpsRagKnowledgeRuntimeResources(")),
                () -> assertTrue(resources.contains("VectorStore vectorStore")),
                () -> assertTrue(resources.contains("EmbeddingModel embeddingModel")),
                () -> assertTrue(resources.contains("public static OpsRagKnowledgeRuntimeResources unavailable()")),
                () -> assertFalse(resources.contains("@Autowired")),
                () -> assertFalse(resources.contains("@Value")),

                () -> assertTrue(policy.contains("final class OpsRagKnowledgePolicy")),
                () -> assertTrue(policy.contains("KnowledgeRetrievalPolicy domainPolicy")),
                () -> assertTrue(policy.contains("domainPolicy.retrievalModeForIteration(")),
                () -> assertTrue(policy.contains("domainPolicy.buildQuery(")),
                () -> assertFalse(policy.contains("LinkedHashSet")),
                () -> assertFalse(policy.contains("DEFAULT_QUERY")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("ProjectDefinitionApplicationService")),
                () -> assertTrue(domainPolicy.contains("public final class KnowledgeRetrievalPolicy")),
                () -> assertTrue(domainPolicy.contains("DEFAULT_QUERY")),
                () -> assertTrue(domainPolicy.contains("LinkedHashSet<String> modes")),
                () -> assertTrue(domainPolicy.contains("检索焦点：")),
                () -> assertFalse(domainPolicy.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(domainPolicy.contains("OpsQuestionContext")),
                () -> assertFalse(domainPolicy.contains("OpsSubAgentDecision")),
                () -> assertFalse(domainPolicy.contains("org.springframework")),
                () -> assertFalse(domainPolicy.contains("cn.lgs.orbisops.trigger")),

                () -> assertTrue(scopeResolver.contains("ProjectKnowledgeAuthorizationApplicationService")),
                () -> assertTrue(scopeResolver.contains("SAFE_KNOWLEDGE_ID")),
                () -> assertTrue(scopeResolver.contains("filterFor(")),
                () -> assertTrue(scopeResolver.contains("KNOWLEDGE_BASE_SCOPE_INVALID")),
                () -> assertFalse(scopeResolver.contains("@Service")),
                () -> assertFalse(scopeResolver.contains("@Component")),

                () -> assertTrue(configuration.contains("@Configuration")),
                () -> assertTrue(configuration.contains("OpsRagKnowledgeSettings opsRagKnowledgeSettings(")),
                () -> assertTrue(configuration.contains("OpsRagKnowledgeRetrievalService opsRagKnowledgeRetrievalService(")),
                () -> assertTrue(configuration.contains("new OpsRagKnowledgeRetrievalService(repository)")),
                () -> assertTrue(configuration.contains("OpsRagKnowledgeRuntimeResources opsRagKnowledgeRuntimeResources(")),
                () -> assertTrue(configuration.contains("@Qualifier(\"ragEmbeddingModel\") ObjectProvider<EmbeddingModel>")),
                () -> assertTrue(configuration.contains("OpsProjectKnowledgeScopeResolver opsProjectKnowledgeScopeResolver(")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProjectKnowledgeAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains("@Value(\"${orbisops.rag.rerank.enabled:false}\")")),

                () -> assertTrue(retrieval.contains("record Input(")),
                () -> assertTrue(retrieval.contains("record Settings(")),
                () -> assertTrue(retrieval.contains("record Result(")),
                () -> assertTrue(retrieval.contains("interface RetrievalExecutor")),
                () -> assertTrue(retrieval.contains("interface CancellationCheck")),
                () -> assertTrue(retrieval.contains("RagAnswerAdvisor")),
                () -> assertTrue(retrieval.contains("SearchRequest.builder()")),
                () -> assertTrue(retrieval.contains("RagRetrievalSettings")),
                () -> assertFalse(retrieval.contains("domain.agent.model.valobj")),
                () -> assertTrue(retrieval.contains("context.put(\"qa_retrieval_mode\"")),
                () -> assertTrue(retrieval.contains("context.put(\"qa_rerank_enabled\"")),
                () -> assertTrue(retrieval.contains("cancellationCheck.check()")),
                () -> assertFalse(retrieval.contains("@Service")),
                () -> assertFalse(retrieval.contains("@Component")),
                () -> assertFalse(retrieval.contains("@Value")),
                () -> assertFalse(retrieval.contains("@Autowired")),
                () -> assertFalse(retrieval.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(retrieval.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(retrieval.contains("OpsQuestionContext")),
                () -> assertFalse(retrieval.contains("OpsSubAgentDecision")),
                () -> assertFalse(retrieval.contains("ProjectDefinitionApplicationService")),
                () -> assertFalse(retrieval.contains("ModelAvailabilityPort")),
                () -> assertTrue(retrieval.lines().count() <= 220));
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
