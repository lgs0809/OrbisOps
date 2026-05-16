package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.ColdMemoryStoreApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCaptureApplicationService;
import cn.lgs.orbisops.application.memory.MemoryContextRenderingApplicationService;
import cn.lgs.orbisops.application.memory.MemoryExtractionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.application.memory.MemoryQueryApplicationService;
import cn.lgs.orbisops.application.memory.MemoryRetrievalApplicationService;
import cn.lgs.orbisops.application.memory.MemorySelectionReferenceApplicationService;
import cn.lgs.orbisops.application.memory.MemorySessionClearApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.SemanticMemoryClearApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryRetrievalApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteApplicationService;
import cn.lgs.orbisops.infrastructure.adapter.memory.InMemoryHotMemoryAdapter;
import cn.lgs.orbisops.infrastructure.adapter.repository.OpsSemanticLexicalRecallAdapter;
import cn.lgs.orbisops.infrastructure.adapter.repository.OpsSemanticLexicalWriteAdapter;
import cn.lgs.orbisops.infrastructure.adapter.repository.OpsSemanticMemoryClearPersistenceAdapter;
import cn.lgs.orbisops.trigger.application.memory.OpsMemoryRetrievalMapper;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticRetrievalFailureAdapter;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticVectorRecallAdapter;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticVectorWriteAdapter;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemorySceneClassificationPolicy;
import cn.lgs.orbisops.domain.memory.service.MemorySelectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMemoryRuntimeTest {

    @Test
    void shouldTrimHotMemoryToConfiguredWindow() {
        InMemoryHotMemoryAdapter store = new InMemoryHotMemoryAdapter(30L);
        for (int i = 0; i < 5; i++) {
            store.append(new MemoryMessageView(
                    "s1",
                    "",
                    "user",
                    "message-" + i,
                    "",
                    Map.of()), 3);
        }

        List<MemoryMessageView> recent = store.recent("s1", 10);

        assertEquals(3, recent.size());
        assertEquals("message-2", recent.get(0).content());
        assertEquals("message-4", recent.get(2).content());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExpireInMemoryHotSessionByTtl() {
        InMemoryHotMemoryAdapter store = new InMemoryHotMemoryAdapter(1L);
        store.append(new MemoryMessageView(
                "s1",
                "",
                "user",
                "expired-message",
                "",
                Map.of()), 3);
        Map<String, Object> buckets = (Map<String, Object>)
                ReflectionTestUtils.getField(store, "sessions");
        Object bucket = buckets.get("s1");
        ReflectionTestUtils.setField(
                bucket,
                "lastAccessAt",
                System.currentTimeMillis() - 61_000L);

        List<MemoryMessageView> recent = store.recent("s1", 10);

        assertTrue(recent.isEmpty());
    }

    @Test
    void shouldTrimLongContextWithHeadAndTail() {
        OpsContextCompressor compressor = new OpsContextCompressor();
        String context = "head-" + "x".repeat(4000) + "-tail";

        String trimmed = compressor.trimContext(context, 1000);

        assertTrue(trimmed.length() < context.length());
        assertTrue(trimmed.contains("memory context compressed"));
        assertTrue(trimmed.startsWith("head-"));
        assertTrue(trimmed.endsWith("-tail"));
    }

    @Test
    void shouldExtractOnlyClassifiedContextMemory() {
        OpsMemoryExtractor extractor = ruleExtractor();
        OpsMemoryMessage message = OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("记住以后默认先给结论再给依据；demo-project 项目目标是外挂型运维 Agent，不是替代原有运维平台。")
                .metadata(Map.of("agentId", "ops", "projectId", "demo-project"))
                .build();

        List<OpsMemoryItem> items = extractor.extract(message);

        assertTrue(items.stream().noneMatch(item -> item.getContent().contains("traceId")));
        assertTrue(items.stream().anyMatch(item -> "USER_WORKFLOW".equals(item.getMemoryType())));
        assertTrue(items.stream().anyMatch(item -> "PROJECT_CONTEXT".equals(item.getMemoryType())));
    }

    @Test
    void shouldNotStoreHardPolicyAsUserPreference() {
        OpsMemoryExtractor extractor = ruleExtractor();

        List<OpsMemoryItem> items = extractor.extract(OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("记住以后所有 HIGH 变更必须审批，HTTP status 409 要返回给前端，沙箱必须验证。")
                .metadata(Map.of("agentId", "ops", "projectId", "demo-project"))
                .build());

        assertTrue(items.isEmpty());
    }

    @Test
    void shouldNotStoreReadOnlyOrChangePackageBoundaryAsPreference() {
        OpsMemoryExtractor extractor = ruleExtractor();

        List<OpsMemoryItem> items = extractor.extract(OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("以后只读检查 demo-project 最近 10 分钟错误日志，不要生成变更包，不要执行生产写操作；ChangePackage 和 LandingRuntime 边界必须保持。")
                .metadata(Map.of("agentId", "ops", "projectId", "demo-project"))
                .build());

        assertTrue(items.isEmpty());
    }

    @Test
    void shouldNotStoreTransientTraceLogMetricRedisOrDockerEvidence() {
        OpsMemoryExtractor extractor = ruleExtractor();

        List<OpsMemoryItem> items = extractor.extract(OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("记住 traceId=abc-123，orderId=20260702001，Redis key sample=order:1，Docker containerId=778899，单次 Prometheus 指标值是 95。")
                .metadata(Map.of("agentId", "ops", "projectId", "demo-project"))
                .build());

        assertTrue(items.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAssembleMemorySlicesInParallel() {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        ObjectProvider<Executor> executorProvider = mock(ObjectProvider.class);
        when(executorProvider.getIfAvailable()).thenReturn(executor);
        SlowHotStore hotStore = new SlowHotStore();
        SlowSemanticStore semanticStore = new SlowSemanticStore();
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                new SlowColdRepository(),
                () -> true,
                null);
        OpsMemoryRetrievalMapper retrievalMapper = new OpsMemoryRetrievalMapper();
        MemoryRetrievalApplicationService retrievalService = new MemoryRetrievalApplicationService(
                coldStore,
                (sessionId, limit) -> retrievalMapper.views(hotStore.recent(sessionId, limit)),
                (sessionId, userId, query, limit) -> retrievalMapper.views(
                        semanticStore.searchMessages(sessionId, userId, query, limit)),
                (scene, userId, projectId, limit) -> List.of(),
                executorProvider::getIfAvailable,
                null);
        MemoryQueryApplicationService queryService = new MemoryQueryApplicationService(
                retrievalService,
                new MemoryContextRenderingApplicationService(),
                mock(MemorySelectionReferenceApplicationService.class),
                new MemorySelectionPolicy(),
                new MemorySceneClassificationPolicy(),
                null);
        OpsMemoryFacade facade = new OpsMemoryFacade(
                queryService,
                mock(MemoryCaptureApplicationService.class),
                mock(MemorySessionClearApplicationService.class),
                new OpsMemoryFacadeSettings(
                        true, 4, 24, 4, 4, 4_000, 2_000L, true, true, 6D));

        long startedNanos = System.nanoTime();
        String context = facade.assembleContext("s1", "u1", "join_metric_5xx");
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        executor.shutdownNow();

        assertTrue(context.contains("cold item"));
        assertTrue(context.contains("hot message"));
        assertTrue(context.contains("semantic message"));
        assertTrue(elapsedMillis < 700, "memory slices should be loaded concurrently, elapsedMillis=" + elapsedMillis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteRawMessageForLexicalRecallWhenEmbeddingUnavailable() {
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        ObjectProvider<JdbcTemplate> jdbcTemplateProvider = mock(ObjectProvider.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        when(availability.isEmbeddingAvailable()).thenReturn(false);
        OpsSemanticRetrievalFailureAdapter failureAdapter =
                new OpsSemanticRetrievalFailureAdapter();
        SemanticMemoryApplicationFacade semanticMemory =
                new SemanticMemoryApplicationFacade(
                        SemanticMemoryWriteApplicationService.withDefaultPolicy(
                                new OpsSemanticVectorWriteAdapter(vectorStoreProvider),
                                new OpsSemanticLexicalWriteAdapter(
                                        jdbcTemplateProvider,
                                        () -> "orbisops_vector_store"),
                                failureAdapter),
                        SemanticMemoryRetrievalApplicationService.withDefaultFusion(
                                new OpsSemanticVectorRecallAdapter(vectorStoreProvider),
                                new OpsSemanticLexicalRecallAdapter(
                                        jdbcTemplateProvider,
                                        () -> "orbisops_vector_store"),
                                failureAdapter),
                        new SemanticMemoryClearApplicationService(
                                new OpsSemanticMemoryClearPersistenceAdapter(
                                        jdbcTemplateProvider,
                                        () -> "orbisops_vector_store"),
                                failureAdapter));
        OpsPgVectorSemanticMemoryStore store =
                new OpsPgVectorSemanticMemoryStore(
                        availability,
                        semanticMemory,
                        8,
                        true,
                        6D);

        store.appendMessage(OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("join_metric_5xx 在 /api/group/join 升高")
                .metadata(Map.of("turn_index", 1))
                .build());

        verify(jdbcTemplate).update(startsWith("INSERT INTO orbisops_vector_store"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("join_metric_5xx 在 /api/group/join 升高"),
                contains("\"memory_kind\":\"message\""));
    }

    private OpsMemoryExtractor ruleExtractor() {
        return new OpsMemoryExtractor(
                MemoryExtractionApplicationService.rulesOnly(() -> "2026-07-28 10:00:00"),
                new OpsMemoryExtractionSettings(true, 10, false, 4_000));
    }

    private static class SlowHotStore {
        public List<OpsMemoryMessage> recent(String sessionId, int maxMessages) {
            sleep();
            return List.of(OpsMemoryMessage.builder()
                    .sessionId(sessionId)
                    .role("user")
                    .content("hot message")
                    .metadata(Map.of("turn_index", 5))
                    .build());
        }
    }

    private static class SlowColdRepository implements IColdMemoryRepository {

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void appendMessage(ColdMemoryMessageSnapshot message) {
        }

        @Override
        public void saveItems(List<ColdMemoryItemSnapshot> items) {
        }

        @Override
        public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
            sleep();
            return List.of(new ColdMemoryItemSnapshot(
                    sessionId,
                    userId,
                    "PROJECT_CONTEXT",
                    "cold item",
                    null,
                    "[]",
                    "user",
                    "hash",
                    Map.of("turn_index", 3),
                    ""));
        }

        @Override
        public void clear(String sessionId) {
        }
    }

    private static class SlowSemanticStore implements OpsSemanticMemoryStore {

        @Override
        public void appendMessage(OpsMemoryMessage message) {
        }

        @Override
        public List<OpsMemoryMessage> searchMessages(String sessionId, String userId, String query, int limit) {
            sleep();
            return List.of(OpsMemoryMessage.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .role("assistant")
                    .content("semantic message")
                    .metadata(Map.of("turn_index", 2))
                    .build());
        }

        @Override
        public void clear(String sessionId) {
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(250L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
