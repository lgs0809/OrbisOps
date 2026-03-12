package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRagBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void settingsMustBeImmutableAndContainNoSpringPropertyInjection() throws IOException {
        String settings = read(RUNTIME + "OpsNodeRagSettings.java");

        assertAll(
                () -> assertTrue(settings.contains("public record OpsNodeRagSettings(")),
                () -> assertTrue(settings.contains("public record Rerank(")),
                () -> assertTrue(settings.contains("public record Ttft(")),
                () -> assertTrue(settings.contains("public record QueryRewrite(")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertFalse(settings.contains("ObjectProvider")),
                () -> assertFalse(settings.contains("Environment")),
                () -> assertTrue(settings.lines().count() < 70));
    }

    @Test
    void configurationMustOwnPropertiesAndOptionalInfrastructureResolution() throws IOException {
        String configuration = read(RUNTIME + "OpsNodeRagConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("Environment environment")),
                () -> assertTrue(configuration.contains("ObjectProvider<VectorStore>")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<RagMultimodalEmbeddingService>")),
                () -> assertTrue(configuration.contains("ObjectProvider<EmbeddingModel>")),
                () -> assertTrue(configuration.contains("new OpsNodeRagSettings(")),
                () -> assertTrue(configuration.contains("new OpsNodeRagAdvisorFactory(")),
                () -> assertTrue(configuration.contains("vectorStoreProvider::getIfAvailable")),
                () -> assertTrue(configuration.contains("embeddingProvider::getIfAvailable")),
                () -> assertTrue(configuration.lines().count() < 110));
    }

    @Test
    void advisorFactoryMustOwnAdvisorConstructionAndModelAvailability() throws IOException {
        String factory = read(RUNTIME + "OpsNodeRagAdvisorFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("Supplier<VectorStore>")),
                () -> assertTrue(factory.contains("Supplier<RagMultimodalEmbeddingService>")),
                () -> assertTrue(factory.contains("Supplier<EmbeddingModel>")),
                () -> assertTrue(factory.contains("ModelAvailabilityPort")),
                () -> assertTrue(factory.contains("IRagKnowledgeRepository")),
                () -> assertTrue(factory.contains("new RagAnswerAdvisor(")),
                () -> assertTrue(factory.contains("RagRetrievalSettings")),
                () -> assertFalse(factory.contains("domain.agent.model.valobj")),
                () -> assertTrue(factory.contains("public record Resources(")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertFalse(factory.contains("@Value")),
                () -> assertTrue(factory.lines().count() < 150));
    }

    @Test
    void plannerMustOwnRetrievalPlanContextAndKnowledgeFilter() throws IOException {
        String planner = read(RUNTIME + "OpsNodeRagRetrievalPlanner.java");

        assertAll(
                () -> assertTrue(planner.contains("public record Plan(")),
                () -> assertTrue(planner.contains("qa_retrieval_mode")),
                () -> assertTrue(planner.contains("qa_vector_top_k")),
                () -> assertTrue(planner.contains("qa_query_rewrite_mode")),
                () -> assertTrue(planner.contains("qa_rerank_enabled")),
                () -> assertTrue(planner.contains("knowledgeFilterExpression(")),
                () -> assertTrue(planner.contains("knowledge_scope == 'PROJECT'")),
                () -> assertFalse(planner.contains("IRagKnowledgeRepository")),
                () -> assertFalse(planner.contains("OpsRuntimeEvent")),
                () -> assertFalse(planner.contains("RagAnswerAdvisor")),
                () -> assertTrue(planner.lines().count() < 180));
    }

    @Test
    void auditRendererMustOwnEventsSourcesAndPromptRendering() throws IOException {
        String renderer = read(RUNTIME + "OpsNodeRagAuditRenderer.java");

        assertAll(
                () -> assertTrue(renderer.contains("RAG_RETRIEVE_STARTED")),
                () -> assertTrue(renderer.contains("RAG_QUERY_REWRITE")),
                () -> assertTrue(renderer.contains("RAG_RERANK")),
                () -> assertTrue(renderer.contains("renderSources(")),
                () -> assertTrue(renderer.contains("renderDocuments(")),
                () -> assertTrue(renderer.contains("public String renderPrompt(")),
                () -> assertFalse(renderer.contains("RagAnswerAdvisor")),
                () -> assertFalse(renderer.contains("IRagKnowledgeRepository")),
                () -> assertFalse(renderer.contains("ObjectProvider")),
                () -> assertFalse(renderer.contains("@Value")),
                () -> assertTrue(renderer.lines().count() < 240));
    }

    @Test
    void serviceMustRemainAFourDependencyOrchestrationFacade() throws IOException {
        String service = read(RUNTIME + "OpsNodeRagService.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "private final OpsNodeRagAdvisorFactory advisorFactory;")),
                () -> assertTrue(service.contains(
                        "private final OpsNodeRagRetrievalPlanner retrievalPlanner;")),
                () -> assertTrue(service.contains(
                        "private final OpsNodeRagAuditRenderer auditRenderer;")),
                () -> assertTrue(service.contains(
                        "private final OpsNodeRagSettings settings;")),
                () -> assertEquals(1, occurrences(service, "public OpsNodeRagService(")),
                () -> assertEquals(5, occurrences(service, "public String enhancePrompt(")),
                () -> assertFalse(service.contains("ObjectProvider")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains(
                        "import org.springframework.ai.vectorstore.VectorStore")),
                () -> assertFalse(service.contains("EmbeddingModel")),
                () -> assertFalse(service.contains("IRagKnowledgeRepository")),
                () -> assertFalse(service.contains("ModelAvailabilityPort")),
                () -> assertFalse(service.contains("AiClientAdvisorVO.RagAnswer")),
                () -> assertFalse(service.contains("RagAnswerAdvisor")),
                () -> assertFalse(service.contains("knowledgeFilterExpression(")),
                () -> assertFalse(service.contains("renderDocuments(")),
                () -> assertFalse(service.contains("addEvent(")),
                () -> assertTrue(service.lines().count() < 170));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
