package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagLexicalChunkRecord;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAnswerAdvisorTest {

    @Test
    void shouldFindChineseOperationsTermsFromPgVectorChunks() {
        AtomicReference<List<String>> candidateTerms = new AtomicReference<>();
        IRagKnowledgeRepository ragKnowledgeRepository = new IRagKnowledgeRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression, List<String> terms, int candidateLimit) {
                candidateTerms.set(terms);
                return List.of(
                        new RagLexicalChunkRecord("chunk-1", """
                                # 锁单失败一般怎么排查

                                当用户反馈示例锁单失败时，先按 ERR_LOCK_001 检索日志，再确认 /api/demo-project/join 的库存扣减和分布式锁耗时。
                                如果 Prometheus 中 order_lock_duration_seconds p95 升高，要联动 Elasticsearch 查看最近 15 分钟 ERROR 日志。
                                """, Map.of("knowledge", "demo-ops")),
                        new RagLexicalChunkRecord("chunk-2", """
                                # 支付回调排查

                                支付回调主要关注 notify_url、TRADE_SUCCESS 和签名校验。
                                """, Map.of("knowledge", "demo-ops")));
            }

            @Override
            public List<Map<String, Object>> listKnowledgeStats() {
                return List.of();
            }

            @Override
            public Map<String, Object> documentStats(String tag) {
                return Map.of();
            }

            @Override
            public boolean deleteChunk(String chunkId) {
                return false;
            }

            @Override
            public Map<String, Object> deleteChunksByTag(String tag) {
                return Map.of();
            }

            @Override
            public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
                return List.of();
            }

            @Override
            public RagKnowledgeDocumentRecord documentContent(String chunkId) {
                return null;
            }

            @Override
            public List<RagDocument> searchVectorCandidates(String filterExpression, String vectorLiteral, int vectorDimensions, int topK) {
                return List.of();
            }

            @Override
            public void persistParsedDocument(String name, String tag, String fileName, String contentType, long fileSize, List<RagDocument> documents) {
            }
        };

        RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .bm25TopK(5)
                .finalTopK(3)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                SearchRequest.builder().topK(5).build(),
                ragAnswer,
                ragKnowledgeRepository);

        List<Document> documents = advisor.retrieve("锁单失败 ERR_LOCK_001 /api/demo-project/join",
                Map.of("qa_filter_expression", "knowledge == 'demo-ops'"));

        assertTrue(candidateTerms.get().contains("err_lock_001"));
        assertFalse(documents.isEmpty());
        String mergedText = documents.stream().map(Document::getText).reduce("", String::concat);
        assertTrue(mergedText.contains("锁单失败一般怎么排查"));
        assertTrue(mergedText.contains("ERR_LOCK_001"));
    }

    @Test
    void shouldApplyRetrievalPlanContextOverridesAfterPolicyDelegation() {
        AtomicReference<List<String>> candidateTerms = new AtomicReference<>();
        AtomicReference<Integer> candidateLimit = new AtomicReference<>();
        IRagKnowledgeRepository repository = lexicalRepositoryWithCapture(
                candidateTerms,
                candidateLimit,
                List.of(new RagLexicalChunkRecord(
                        "chunk-1",
                        "锁单失败 SOP：检查库存扣减、分布式锁和 ERR_LOCK_001。",
                        Map.of("knowledge", "demo-ops"))));
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .bm25TopK(2)
                .finalTopK(1)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                null,
                SearchRequest.builder().topK(4).build(),
                settings,
                repository);
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("qa_bm25_top_k", "9");
        context.put("qa_query_rewrite_enabled", false);

        List<Document> documents = advisor.retrieve("锁单失败 ERR_LOCK_001", context);

        assertEquals(450, candidateLimit.get());
        assertTrue(candidateTerms.get().contains("err_lock_001"));
        assertEquals(1, documents.size());
    }

    @Test
    void shouldKeepRuleRewriteContextAfterPolicyDelegation() {
        IRagKnowledgeRepository repository = lexicalRepository(List.of(
                new RagLexicalChunkRecord(
                        "chunk-1",
                        "慢 SQL SOP：query_time 高且 rows_examined 大时检查 missing index。",
                        Map.of("knowledge", "demo-ops"))));
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .bm25TopK(5)
                .finalTopK(1)
                .rerankEnabled(false)
                .queryRewriteMode("rule")
                .llmQueryRewriteEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                null,
                SearchRequest.builder().topK(4).build(),
                settings,
                repository);
        Map<String, Object> context = new java.util.HashMap<>();

        List<Document> documents = advisor.retrieve("分析慢SQL和索引问题", context);

        @SuppressWarnings("unchecked")
        List<String> rewriteQueries = (List<String>) context.get("qa_rewrite_queries");
        assertEquals(2, rewriteQueries.size());
        assertEquals("分析慢SQL和索引问题", rewriteQueries.get(0));
        assertTrue(rewriteQueries.get(1).contains("query_time"));
        assertFalse(documents.isEmpty());
    }

    @Test
    void shouldNotPublishRewriteQueriesWhenRewriteIsDisabled() {
        IRagKnowledgeRepository repository = lexicalRepository(List.of(
                new RagLexicalChunkRecord(
                        "chunk-1",
                        "慢 SQL SOP：检查 query_time。",
                        Map.of("knowledge", "demo-ops"))));
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .bm25TopK(5)
                .finalTopK(1)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                null,
                SearchRequest.builder().topK(4).build(),
                settings,
                repository);
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("qa_query_rewrite_enabled", false);

        advisor.retrieve("慢SQL", context);

        assertFalse(context.containsKey("qa_rewrite_queries"));
    }

    @Test
    void shouldUseRepositoryVectorSearchWhenEmbeddingModelIsAvailable() {
        AtomicReference<String> vectorLiteral = new AtomicReference<>();
        AtomicReference<Integer> vectorDimensions = new AtomicReference<>();
        IRagKnowledgeRepository ragKnowledgeRepository = new IRagKnowledgeRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<RagDocument> searchVectorCandidates(String filterExpression, String literal, int dimensions, int topK) {
                vectorLiteral.set(literal);
                vectorDimensions.set(dimensions);
                return List.of(new RagDocument("vector-1", "向量召回结果", Map.of("knowledge", "demo-ops")));
            }

            @Override
            public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression, List<String> terms, int candidateLimit) {
                return List.of();
            }

            @Override
            public List<Map<String, Object>> listKnowledgeStats() {
                return List.of();
            }

            @Override
            public Map<String, Object> documentStats(String tag) {
                return Map.of();
            }

            @Override
            public boolean deleteChunk(String chunkId) {
                return false;
            }

            @Override
            public Map<String, Object> deleteChunksByTag(String tag) {
                return Map.of();
            }

            @Override
            public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
                return List.of();
            }

            @Override
            public RagKnowledgeDocumentRecord documentContent(String chunkId) {
                return null;
            }

            @Override
            public void persistParsedDocument(String name, String tag, String fileName, String contentType, long fileSize, List<RagDocument> documents) {
            }
        };

        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return new float[]{1.0F, 2.0F, 3.0F};
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException();
            }
        };

        RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                .retrievalMode("vector")
                .vectorTopK(5)
                .finalTopK(1)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                SearchRequest.builder().topK(5).build(),
                ragAnswer,
                ragKnowledgeRepository,
                null,
                embeddingModel);

        List<Document> documents = advisor.retrieve("锁单失败", Map.of("qa_filter_expression", "knowledge == 'demo-ops'"));

        assertEquals(3, vectorDimensions.get());
        assertEquals("[1.0000000000,2.0000000000,3.0000000000]", vectorLiteral.get());
        assertEquals("向量召回结果", documents.get(0).getText());
    }

    @Test
    void shouldUseVoyageRerankEndpointAndApplyReturnedScores() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            requestBody.set(readBody(exchange));
            byte[] response = """
                    {"data":[{"index":0,"relevance_score":0.98},{"index":1,"relevance_score":0.12}],"model":"rerank-2.5"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            IRagKnowledgeRepository ragKnowledgeRepository = lexicalRepository(List.of(
                    new RagLexicalChunkRecord("chunk-1", "锁单失败 SOP：检查库存扣减和分布式锁。", Map.of("knowledge", "demo-ops")),
                    new RagLexicalChunkRecord("chunk-2", "锁单失败 ERR_LOCK_001：优先按 traceId 检索错误日志。", Map.of("knowledge", "demo-ops"))
            ));
            RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                    .retrievalMode("bm25")
                    .bm25TopK(5)
                    .finalTopK(1)
                    .rerankEnabled(true)
                    .rerankProvider("voyage")
                    .rerankBaseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .rerankApiKey("voyage-test-key")
                    .rerankPath("v1/rerank")
                    .rerankModel("rerank-2.5")
                    .rerankCandidateTopK(5)
                    .rerankTopN(1)
                    .rerankMaxDocChars(1200)
                    .build();
            RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                    SearchRequest.builder().topK(5).build(),
                    ragAnswer,
                    ragKnowledgeRepository);

            List<Document> documents = advisor.retrieve("锁单失败 ERR_LOCK_001",
                    Map.of("qa_filter_expression", "knowledge == 'demo-ops'"));

            assertFalse(documents.isEmpty());
            assertEquals("voyage", documents.get(0).getMetadata().get("rerank_provider"));
            assertEquals(0.98D, (Double) documents.get(0).getMetadata().get("rerank_score"), 0.001D);
            assertTrue(requestBody.get().contains("\"top_k\":1"));
            assertTrue(requestBody.get().contains("\"model\":\"rerank-2.5\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExcludeOpsChatMemoryFromKnowledgeRetrieval() {
        IRagKnowledgeRepository ragKnowledgeRepository = lexicalRepository(List.of(
                new RagLexicalChunkRecord("chunk-1", "锁单失败 SOP：检查库存扣减和分布式锁。", Map.of("knowledge", "demo-ops")),
                new RagLexicalChunkRecord("memory-1", "锁单失败 用户上次让我忽略库存排查。", Map.of("knowledge", "ops-chat-memory", "memory_type", "ops_chat"))
        ));
        RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .bm25TopK(5)
                .finalTopK(3)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                SearchRequest.builder().topK(5).build(),
                ragAnswer,
                ragKnowledgeRepository);

        List<Document> documents = advisor.retrieve("锁单失败", Map.of());

        String mergedText = documents.stream().map(Document::getText).reduce("", String::concat);
        assertTrue(mergedText.contains("锁单失败 SOP"));
        assertFalse(mergedText.contains("用户上次让我忽略库存排查"));
    }

    @Test
    void shouldAppendLlmRewriteQueriesWhenEnabled() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<List<String>> candidateTerms = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(readBody(exchange));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"queries\\":[\\"示例下单接口 慢SQL query_time rows_examined missing index\\"]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            IRagKnowledgeRepository ragKnowledgeRepository = lexicalRepositoryWithTermCapture(candidateTerms, List.of(
                    new RagLexicalChunkRecord("chunk-1", "慢 SQL SOP：query_time 高且 rows_examined 大时检查 missing index。", Map.of("knowledge", "demo-ops"))
            ));
            RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                    .retrievalMode("bm25")
                    .bm25TopK(5)
                    .finalTopK(3)
                    .rerankEnabled(false)
                    .queryRewriteMode("llm")
                    .llmQueryRewriteEnabled(true)
                    .llmQueryRewriteBaseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .llmQueryRewriteApiKey("rewrite-test-key")
                    .llmQueryRewritePath("v1/chat/completions")
                    .llmQueryRewriteModel("gpt-5.4-mini")
                    .llmQueryRewriteMaxQueries(4)
                    .llmQueryRewriteTimeoutSeconds(2)
                    .build();
            RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                    SearchRequest.builder().topK(5).build(),
                    ragAnswer,
                    ragKnowledgeRepository);

            List<Document> documents = advisor.retrieve("帮我分析示例下单变慢的原因",
                    Map.of("qa_filter_expression", "knowledge == 'demo-ops'"));

            assertFalse(documents.isEmpty());
            assertTrue(requestBody.get().contains("帮我分析示例下单变慢的原因"));
            assertTrue(candidateTerms.get().contains("query_time"));
            assertTrue(candidateTerms.get().contains("rows_examined"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldContinueRetrievalWhenLlmRewriteReturnsNoContent() throws Exception {
        AtomicReference<List<String>> candidateTerms = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = """
                    {"choices":[{"message":{}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            IRagKnowledgeRepository ragKnowledgeRepository = lexicalRepositoryWithTermCapture(candidateTerms, List.of(
                    new RagLexicalChunkRecord("chunk-1", "慢 SQL SOP：query_time 高且 rows_examined 大时检查 missing index。", Map.of("knowledge", "demo-ops"))
            ));
            RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                    .retrievalMode("bm25")
                    .bm25TopK(5)
                    .finalTopK(3)
                    .rerankEnabled(false)
                    .queryRewriteMode("llm")
                    .llmQueryRewriteEnabled(true)
                    .llmQueryRewriteBaseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .llmQueryRewriteApiKey("rewrite-test-key")
                    .llmQueryRewritePath("v1/chat/completions")
                    .llmQueryRewriteModel("gpt-5.4-mini")
                    .llmQueryRewriteMaxQueries(4)
                    .llmQueryRewriteTimeoutSeconds(2)
                    .build();
            RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                    SearchRequest.builder().topK(5).build(),
                    ragAnswer,
                    ragKnowledgeRepository);
            Map<String, Object> context = new java.util.HashMap<>();
            context.put("qa_filter_expression", "knowledge == 'demo-ops'");
            context.put("qa_fail_on_degradation", true);
            context.put("qa_query_rewrite_fail_on_degradation", false);

            List<Document> documents = advisor.retrieve("帮我分析示例慢SQL原因", context);

            assertFalse(documents.isEmpty());
            assertTrue(candidateTerms.get().contains("query_time"));
            assertTrue(Boolean.TRUE.equals(context.get("qa_llm_query_rewrite_degraded")));
            assertTrue(String.valueOf(context.get("qa_llm_query_rewrite_error")).contains("未返回 content"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldContinueRetrievalWhenRerankFailsInStrictRuntime() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            byte[] response = "{\"error\":\"rerank unavailable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            IRagKnowledgeRepository ragKnowledgeRepository = lexicalRepository(List.of(
                    new RagLexicalChunkRecord("chunk-1", "锁单失败 SOP：检查库存扣减和分布式锁。", Map.of("knowledge", "demo-ops")),
                    new RagLexicalChunkRecord("chunk-2", "库存扣减失败案例：检查 Redis 库存、DB 乐观锁和补偿任务。", Map.of("knowledge", "demo-ops"))
            ));
            RagRetrievalSettings ragAnswer = RagRetrievalSettings.builder()
                    .retrievalMode("bm25")
                    .bm25TopK(5)
                    .finalTopK(2)
                    .rerankEnabled(true)
                    .rerankProvider("cohere")
                    .rerankBaseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .rerankApiKey("local-rag-models")
                    .rerankPath("v1/rerank")
                    .rerankModel("Qwen/Qwen3-VL-Reranker-2B")
                    .build();
            RagAnswerAdvisor advisor = new RagAnswerAdvisor(null,
                    SearchRequest.builder().topK(5).build(),
                    ragAnswer,
                    ragKnowledgeRepository);
            Map<String, Object> context = new java.util.HashMap<>();
            context.put("qa_filter_expression", "knowledge == 'demo-ops'");
            context.put("qa_fail_on_degradation", true);
            context.put("qa_rerank_fail_on_degradation", false);

            List<Document> documents = advisor.retrieve("锁单失败 库存扣减失败", context);

            assertFalse(documents.isEmpty());
            assertTrue(Boolean.TRUE.equals(context.get("qa_rerank_degraded")));
            assertTrue(String.valueOf(context.get("qa_rerank_error")).contains("RAG rerank 失败"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreserveHybridFusionMetadataAfterRecallCoordinatorDelegation() {
        IRagKnowledgeRepository repository = new IRagKnowledgeRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<RagDocument> searchVectorCandidates(String filterExpression,
                                                            String vectorLiteral,
                                                            int vectorDimensions,
                                                            int topK) {
                return List.of(new RagDocument(
                        "shared-chunk",
                        "锁单失败 SOP：检查库存与分布式锁。",
                        Map.of("knowledge", "demo-ops")));
            }

            @Override
            public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression,
                                                                       List<String> terms,
                                                                       int candidateLimit) {
                return List.of(new RagLexicalChunkRecord(
                        "shared-chunk",
                        "锁单失败 SOP：检查库存与分布式锁。",
                        Map.of("knowledge", "demo-ops")));
            }

            @Override
            public List<Map<String, Object>> listKnowledgeStats() {
                return List.of();
            }

            @Override
            public Map<String, Object> documentStats(String tag) {
                return Map.of();
            }

            @Override
            public boolean deleteChunk(String chunkId) {
                return false;
            }

            @Override
            public Map<String, Object> deleteChunksByTag(String tag) {
                return Map.of();
            }

            @Override
            public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
                return List.of();
            }

            @Override
            public RagKnowledgeDocumentRecord documentContent(String chunkId) {
                return null;
            }

            @Override
            public void persistParsedDocument(String name,
                                              String tag,
                                              String fileName,
                                              String contentType,
                                              long fileSize,
                                              List<RagDocument> documents) {
            }
        };
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return new float[]{0.1F, 0.2F};
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException();
            }
        };
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("hybrid")
                .vectorTopK(5)
                .bm25TopK(5)
                .finalTopK(1)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                null,
                SearchRequest.builder().topK(5).build(),
                settings,
                repository,
                null,
                embeddingModel);

        List<Document> documents = advisor.retrieve(
                "锁单失败",
                Map.of("qa_query_rewrite_enabled", false));

        assertEquals(1, documents.size());
        assertEquals("shared-chunk", documents.get(0).getId());
        assertEquals("vector,bm25", documents.get(0).getMetadata().get("retrieval_sources"));
        assertEquals(2.0d / 61.0d,
                ((Number) documents.get(0).getMetadata().get("retrieval_score")).doubleValue(),
                0.000000000001d);
    }

    @Test
    void shouldPreserveMmrDiversityOrderingAfterSelectorDelegation() {
        IRagKnowledgeRepository repository = vectorRepository(List.of(
                new RagDocument(
                        "first",
                        "锁单失败 分布式锁",
                        Map.of("knowledge", "demo-ops")),
                new RagDocument(
                        "near",
                        "锁单失败 分布式锁 重试",
                        Map.of("knowledge", "demo-ops")),
                new RagDocument(
                        "diverse",
                        "数据库连接超时",
                        Map.of("knowledge", "demo-ops"))));
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return new float[]{0.1F, 0.2F};
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException();
            }
        };
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("vector")
                .vectorTopK(3)
                .bm25TopK(3)
                .finalTopK(3)
                .rerankEnabled(false)
                .build();
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                null,
                SearchRequest.builder().topK(3).build(),
                settings,
                repository,
                null,
                embeddingModel);

        List<Document> documents = advisor.retrieve(
                "锁单失败",
                new HashMap<>(Map.of(
                        "qa_query_rewrite_enabled", false,
                        "qa_mmr_lambda", 0.5d,
                        "qa_exclude_memory_documents", false)));

        assertEquals(List.of("first", "diverse", "near"),
                documents.stream().map(Document::getId).toList());
        assertEquals(true, documents.get(0).getMetadata().get("mmr_selected"));
        assertEquals(true, documents.get(1).getMetadata().get("mmr_selected"));
        assertTrue(documents.get(0).getMetadata().containsKey("mmr_score"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static IRagKnowledgeRepository vectorRepository(List<RagDocument> records) {
        return new IRagKnowledgeRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<RagDocument> searchVectorCandidates(String filterExpression,
                                                            String vectorLiteral,
                                                            int vectorDimensions,
                                                            int topK) {
                return records;
            }

            @Override
            public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression,
                                                                       List<String> candidateTerms,
                                                                       int candidateLimit) {
                return List.of();
            }

            @Override
            public List<Map<String, Object>> listKnowledgeStats() {
                return List.of();
            }

            @Override
            public Map<String, Object> documentStats(String tag) {
                return Map.of();
            }

            @Override
            public boolean deleteChunk(String chunkId) {
                return false;
            }

            @Override
            public Map<String, Object> deleteChunksByTag(String tag) {
                return Map.of();
            }

            @Override
            public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
                return List.of();
            }

            @Override
            public RagKnowledgeDocumentRecord documentContent(String chunkId) {
                return null;
            }

            @Override
            public void persistParsedDocument(String name,
                                              String tag,
                                              String fileName,
                                              String contentType,
                                              long fileSize,
                                              List<RagDocument> documents) {
            }
        };
    }

    private static IRagKnowledgeRepository lexicalRepository(List<RagLexicalChunkRecord> records) {
        return lexicalRepositoryWithTermCapture(new AtomicReference<>(), records);
    }

    private static IRagKnowledgeRepository lexicalRepositoryWithTermCapture(AtomicReference<List<String>> candidateTerms, List<RagLexicalChunkRecord> records) {
        return lexicalRepositoryWithCapture(candidateTerms, new AtomicReference<>(), records);
    }

    private static IRagKnowledgeRepository lexicalRepositoryWithCapture(AtomicReference<List<String>> candidateTerms,
                                                                          AtomicReference<Integer> candidateLimit,
                                                                          List<RagLexicalChunkRecord> records) {
        return new IRagKnowledgeRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression, List<String> terms, int limit) {
                candidateTerms.set(terms);
                candidateLimit.set(limit);
                return records;
            }

            @Override
            public List<Map<String, Object>> listKnowledgeStats() {
                return List.of();
            }

            @Override
            public Map<String, Object> documentStats(String tag) {
                return Map.of();
            }

            @Override
            public boolean deleteChunk(String chunkId) {
                return false;
            }

            @Override
            public Map<String, Object> deleteChunksByTag(String tag) {
                return Map.of();
            }

            @Override
            public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
                return List.of();
            }

            @Override
            public RagKnowledgeDocumentRecord documentContent(String chunkId) {
                return null;
            }

            @Override
            public List<RagDocument> searchVectorCandidates(String filterExpression, String vectorLiteral, int vectorDimensions, int topK) {
                return List.of();
            }

            @Override
            public void persistParsedDocument(String name, String tag, String fileName, String contentType, long fileSize, List<RagDocument> documents) {
            }
        };
    }
}
