package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.ContextMemoryAdminApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.ContextMemoryQueryApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemorySceneQueryApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryStoreApplicationService;
import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryProjectionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsContextMemoryServiceTest {

    @Test
    void unavailableRepositoryKeepsListEmptyAndMutationFailClosed() {
        InMemoryRepository repository = new InMemoryRepository(false);
        OpsContextMemoryService service = service(repository);

        assertEquals(List.of(), service.list("PROJECT", "demo-project", "PROJECT_CONTEXT", "ACTIVE", 10));
        assertThrows(IllegalStateException.class, () -> service.create(validRequest("ctx-1")));
    }

    @Test
    void createNormalizesAndPersistsTypedSnapshot() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        Map<String, Object> request = new LinkedHashMap<>(validRequest("ctx-1"));
        request.put("scopeType", " project ");
        request.put("memoryType", " project_context ");
        request.put("confidence", 2D);
        request.put("keywords", List.of("ddd", "memory"));

        Map<String, Object> created = service.create(request);

        assertEquals("PROJECT", created.get("scopeType"));
        assertEquals("PROJECT_CONTEXT", created.get("memoryType"));
        assertEquals(BigDecimal.ONE, created.get("confidence"));
        assertTrue(String.valueOf(created.get("keywords")).contains("ddd"));
        assertEquals("ctx-1", repository.snapshots.get("ctx-1").memoryId());
    }

    @Test
    void createWithoutIdentityUsesApplicationGenerator() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        Map<String, Object> request = new LinkedHashMap<>(validRequest("ctx-1"));
        request.remove("memoryId");

        Map<String, Object> created = service.create(request);

        assertEquals("ctx-mem-generated-id", created.get("memoryId"));
    }

    @Test
    void duplicateCreateIsRejectedBeforeUpsert() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        service.create(validRequest("ctx-1"));

        assertThrows(IllegalArgumentException.class, () -> service.create(validRequest("ctx-1")));
    }

    @Test
    void updateStatusReturnsUpdatedCompatibilityView() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        service.create(validRequest("ctx-1"));

        Map<String, Object> updated = service.updateStatus("ctx-1", "archived");

        assertEquals("ARCHIVED", updated.get("status"));
    }

    @Test
    void sceneQueryReturnsCompatibilityMapsFromTypedApplicationResult() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        service.create(memoryRequest("glossary", "PROJECT", "demo-project", "PROJECT_GLOSSARY"));
        service.create(memoryRequest("convention", "PROJECT", "demo-project", "PROJECT_CONVENTION"));
        service.create(memoryRequest("preference", "USER", "user-1", "USER_PREFERENCE"));

        List<Map<String, Object>> result = service.listForScene(
                "OPS_TROUBLESHOOTING", "user-1", "demo-project", 4);

        assertEquals(List.of("glossary", "convention", "preference"),
                result.stream().map(item -> String.valueOf(item.get("memoryId"))).toList());
    }

    @Test
    void persistsTypedExtractedItemsThroughDomainProjection() {
        InMemoryRepository repository = new InMemoryRepository(true);
        OpsContextMemoryService service = service(repository);
        ColdMemoryItemSnapshot item = new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "PROJECT_CONTEXT",
                "DDD migration",
                BigDecimal.valueOf(0.9D),
                "[]",
                "user",
                "source-hash",
                Map.of("projectId", "demo-project"),
                "");

        service.saveExtractedItems(List.of(item));

        assertEquals(1, repository.snapshots.size());
        ContextMemorySnapshot saved = repository.snapshots.values().iterator().next();
        assertEquals("PROJECT", saved.scopeType());
        assertEquals("demo-project", saved.scopeId());
        assertEquals("DDD migration", saved.summary());
        assertTrue(saved.memoryId().startsWith("ctx-mem-"));
    }

    private OpsContextMemoryService service(IContextMemoryRepository repository) {
        ContextMemoryStoreApplicationService storeService = new ContextMemoryStoreApplicationService(
                repository,
                new ContextMemoryProjectionPolicy(),
                null,
                null);
        ContextMemoryAdminApplicationService adminService = new ContextMemoryAdminApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy(),
                () -> "generated-id");
        ContextMemoryQueryApplicationService queryService = new ContextMemoryQueryApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy());
        ContextMemorySceneQueryApplicationService sceneQueryService =
                new ContextMemorySceneQueryApplicationService(
                        storeService,
                        new ContextMemoryDefinitionPolicy());
        return new OpsContextMemoryService(new ContextMemoryApplicationFacade(
                storeService,
                queryService,
                adminService,
                sceneQueryService));
    }

    private Map<String, Object> memoryRequest(String memoryId,
                                              String scopeType,
                                              String scopeId,
                                              String memoryType) {
        return Map.of(
                "memoryId", memoryId,
                "scopeType", scopeType,
                "scopeId", scopeId,
                "memoryType", memoryType,
                "title", memoryId,
                "summary", memoryId,
                "content", memoryId,
                "status", "ACTIVE");
    }

    private Map<String, Object> validRequest(String memoryId) {
        return Map.of(
                "memoryId", memoryId,
                "scopeType", "PROJECT",
                "scopeId", "demo-project",
                "memoryType", "PROJECT_CONTEXT",
                "title", "DDD migration",
                "summary", "summary",
                "content", "content",
                "status", "ACTIVE",
                "confidence", BigDecimal.valueOf(0.9D));
    }

    private static class InMemoryRepository implements IContextMemoryRepository {

        private final boolean available;
        private final Map<String, ContextMemorySnapshot> snapshots = new LinkedHashMap<>();

        private InMemoryRepository(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<ContextMemorySnapshot> search(ContextMemorySearchCriteria criteria) {
            return snapshots.values().stream()
                    .filter(snapshot -> blank(criteria.scopeType()) || criteria.scopeType().equals(snapshot.scopeType()))
                    .filter(snapshot -> blank(criteria.scopeId()) || criteria.scopeId().equals(snapshot.scopeId()))
                    .filter(snapshot -> blank(criteria.memoryType()) || criteria.memoryType().equals(snapshot.memoryType()))
                    .filter(snapshot -> blank(criteria.status()) || criteria.status().equals(snapshot.status()))
                    .limit(criteria.limit())
                    .toList();
        }

        @Override
        public Optional<ContextMemorySnapshot> findByMemoryId(String memoryId) {
            return Optional.ofNullable(snapshots.get(memoryId));
        }

        @Override
        public boolean exists(String memoryId) {
            return snapshots.containsKey(memoryId);
        }

        @Override
        public void upsert(ContextMemorySnapshot snapshot) {
            snapshots.put(snapshot.memoryId(), snapshot);
        }

        @Override
        public boolean updateStatus(String memoryId, String status) {
            ContextMemorySnapshot before = snapshots.get(memoryId);
            if (before == null) return false;
            snapshots.put(memoryId, new ContextMemorySnapshot(
                    before.id(),
                    before.memoryId(),
                    before.scopeType(),
                    before.scopeId(),
                    before.memoryType(),
                    before.title(),
                    before.summary(),
                    before.content(),
                    before.keywords(),
                    status,
                    before.confidence(),
                    before.sourceType(),
                    before.sourceId(),
                    before.sourceMessageHash(),
                    before.createdBy(),
                    before.createTime(),
                    before.updateTime(),
                    before.expireTime()));
            return true;
        }

        private boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
