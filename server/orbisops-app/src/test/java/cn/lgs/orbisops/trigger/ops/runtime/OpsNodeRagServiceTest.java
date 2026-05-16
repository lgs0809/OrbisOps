package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsNodeRagServiceTest {

    @Test
    void shouldPropagateProjectKnowledgeScopeIntoRetrievalFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);

        when(availability.isEmbeddingAvailable()).thenReturn(true);
        when(availability.isRerankAvailable(anyString())).thenReturn(false);
        when(availability.isChatAvailable()).thenReturn(false);
        when(repository.available()).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(
                new float[]{0.1F, 0.2F, 0.3F});
        when(repository.searchVectorCandidates(
                anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new RagDocument(
                        "doc-1",
                        "project evidence",
                        Map.of("knowledge", "runbook"))));

        OpsNodeRagService service = service(
                vectorStore,
                embeddingModel,
                availability,
                repository,
                settings(false, "", false, true));

        service.enhancePrompt(
                "diagnose lock failure",
                "lock failure",
                true,
                "runbook",
                new ArrayList<>(),
                null,
                false,
                "demo-project",
                "PROJECT");

        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).searchVectorCandidates(
                filterCaptor.capture(), anyString(), anyInt(), anyInt());
        assertEquals(
                "knowledge == 'runbook' && knowledge_scope == 'PROJECT' && project_id == 'demo-project'",
                filterCaptor.getValue());
    }

    @Test
    void shouldUseOriginalQueryAndKeepRerankForAgentEventStreams() throws Exception {
        AtomicReference<String> embeddedQuery = new AtomicReference<>();
        AtomicReference<String> rerankBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            rerankBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"results":[{"index":1,"relevance_score":0.95},{"index":0,"relevance_score":0.42}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            VectorStore vectorStore = mock(VectorStore.class);
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
            ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);

            when(availability.isEmbeddingAvailable()).thenReturn(true);
            when(availability.isRerankAvailable(anyString())).thenReturn(true);
            when(availability.isChatAvailable()).thenReturn(false);
            when(repository.available()).thenReturn(true);
            when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
                embeddedQuery.set(invocation.getArgument(0));
                return new float[]{0.1F, 0.2F, 0.3F};
            });
            when(repository.searchVectorCandidates(
                    anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of(
                            new RagDocument(
                                    "doc-1",
                                    "无关知识",
                                    Map.of(
                                            "source", "other.md",
                                            "knowledge", "demo-ops")),
                            new RagDocument(
                                    "doc-2",
                                    "锁单失败 SOP",
                                    Map.of(
                                            "source", "lock-sop.md",
                                            "knowledge", "demo-ops"))));

            OpsNodeRagService service = service(
                    vectorStore,
                    embeddingModel,
                    availability,
                    repository,
                    settings(
                            true,
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            false,
                            true));

            List<OpsRuntimeEvent> events = new ArrayList<>();
            List<OpsRuntimeEvent> streamedEvents = new ArrayList<>();
            String prompt = service.enhancePrompt(
                    "包含大量日志和路由契约的节点 Prompt",
                    "锁单失败 SOP",
                    true,
                    "demo-ops",
                    events,
                    streamedEvents::add,
                    false);

            assertEquals("锁单失败 SOP", embeddedQuery.get());
            assertTrue(rerankBody.get().contains("Qwen/Qwen3-VL-Reranker-2B"));
            assertTrue(prompt.indexOf("锁单失败 SOP") < prompt.indexOf("无关知识"));
            assertTrue(events.stream().anyMatch(event ->
                    "RAG_RETRIEVE".equals(event.getEventType())
                            && Boolean.TRUE.equals(
                            event.getPayload().get("rerankEnabled"))));
            assertEquals(events.size(), streamedEvents.size());
        } finally {
            server.stop(0);
        }
    }

    private OpsNodeRagService service(
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            ModelAvailabilityPort availability,
            IRagKnowledgeRepository repository,
            OpsNodeRagSettings settings) {
        OpsNodeRagAdvisorFactory advisorFactory = new OpsNodeRagAdvisorFactory(
                () -> vectorStore,
                () -> null,
                () -> embeddingModel,
                availability,
                repository);
        return new OpsNodeRagService(
                advisorFactory,
                new OpsNodeRagRetrievalPlanner(),
                new OpsNodeRagAuditRenderer(),
                settings);
    }

    private OpsNodeRagSettings settings(
            boolean rerankEnabled,
            String rerankBaseUrl,
            boolean llmRewriteEnabled,
            boolean failOnDegradation) {
        return new OpsNodeRagSettings(
                new OpsNodeRagSettings.Rerank(
                        rerankEnabled,
                        "cohere",
                        rerankBaseUrl,
                        "local-rag-models",
                        "v1/rerank",
                        "Qwen/Qwen3-VL-Reranker-2B",
                        8,
                        2,
                        1200),
                new OpsNodeRagSettings.Ttft(
                        true, true, 4, 6, 4, true),
                new OpsNodeRagSettings.QueryRewrite(
                        "rule",
                        llmRewriteEnabled,
                        "",
                        "",
                        "v1/chat/completions",
                        "gpt-5.4-mini",
                        4,
                        2,
                        18,
                        true,
                        2),
                failOnDegradation);
    }
}
