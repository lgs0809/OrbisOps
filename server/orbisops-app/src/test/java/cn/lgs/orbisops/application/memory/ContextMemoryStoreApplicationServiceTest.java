package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryProjectionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryStoreApplicationServiceTest {

    @Test
    void unavailableStoreKeepsSearchOpenAndMutationsClosed() {
        InMemoryRepository repository = new InMemoryRepository(false);
        ContextMemoryStoreApplicationService service = service(repository, new ArrayList<>(), new ArrayList<>());

        assertEquals(List.of(), service.search(new ContextMemorySearchCriteria("", "", "", "", 10)));
        assertThrows(IllegalStateException.class, () -> service.require("ctx-1"));
        assertThrows(IllegalStateException.class, () -> service.create(snapshot("ctx-1")));
        service.saveExtractedItems(List.of(extracted("content", "demo-project")));
        assertTrue(repository.snapshots.isEmpty());
    }

    @Test
    void createRejectsDuplicatesAndEmitsTypedAuditAfterRefresh() {
        InMemoryRepository repository = new InMemoryRepository(true);
        List<ContextMemoryAuditEvent> audits = new ArrayList<>();
        ContextMemoryStoreApplicationService service = service(repository, audits, new ArrayList<>());

        ContextMemorySnapshot created = service.create(snapshot("ctx-1"));

        assertEquals("ctx-1", created.memoryId());
        assertEquals(1, audits.size());
        assertEquals("create", audits.get(0).action());
        assertEquals("ctx-1", audits.get(0).snapshot().memoryId());
        assertThrows(IllegalArgumentException.class, () -> service.create(snapshot("ctx-1")));
    }

    @Test
    void upsertAndStatusUpdateReturnAuthoritativeSnapshots() {
        InMemoryRepository repository = new InMemoryRepository(true);
        List<ContextMemoryAuditEvent> audits = new ArrayList<>();
        ContextMemoryStoreApplicationService service = service(repository, audits, new ArrayList<>());

        service.upsert(snapshot("ctx-1"));
        ContextMemorySnapshot updated = service.updateStatus("ctx-1", "ARCHIVED");

        assertEquals("ARCHIVED", updated.status());
        assertEquals(List.of("upsert", "update-status"),
                audits.stream().map(ContextMemoryAuditEvent::action).toList());
    }

    @Test
    void projectionFailureIsIsolatedAndRemainingItemsPersist() {
        InMemoryRepository repository = new InMemoryRepository(true);
        repository.failContent = "broken";
        List<String> failures = new ArrayList<>();
        List<ContextMemoryAuditEvent> audits = new ArrayList<>();
        ContextMemoryStoreApplicationService service = service(repository, audits, failures);

        service.saveExtractedItems(List.of(
                extracted("broken", "demo-project"),
                extracted("healthy", "demo-project")));

        assertEquals(1, repository.snapshots.size());
        assertEquals("healthy", repository.snapshots.values().iterator().next().content());
        assertEquals(List.of("projection-upsert:broken persistence"), failures);
        assertEquals(1, audits.size());
        assertEquals("upsert", audits.get(0).action());
    }

    @Test
    void auditObserverFailureDoesNotRollBackAuthoritativeMutation() {
        InMemoryRepository repository = new InMemoryRepository(true);
        ContextMemoryStoreApplicationService service = new ContextMemoryStoreApplicationService(
                repository,
                new ContextMemoryProjectionPolicy(),
                event -> { throw new IllegalStateException("audit down"); },
                null);

        ContextMemorySnapshot created = service.create(snapshot("ctx-1"));

        assertEquals("ctx-1", created.memoryId());
        assertTrue(repository.snapshots.containsKey("ctx-1"));
    }

    private ContextMemoryStoreApplicationService service(InMemoryRepository repository,
                                                         List<ContextMemoryAuditEvent> audits,
                                                         List<String> failures) {
        return new ContextMemoryStoreApplicationService(
                repository,
                new ContextMemoryProjectionPolicy(),
                audits::add,
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));
    }

    private ContextMemorySnapshot snapshot(String memoryId) {
        return new ContextMemorySnapshot(
                null,
                memoryId,
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "DDD migration",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.9D),
                "manual",
                "session-1",
                "source-hash",
                "user-1",
                "",
                "",
                "");
    }

    private ColdMemoryItemSnapshot extracted(String content, String projectId) {
        return new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "PROJECT_CONTEXT",
                content,
                BigDecimal.valueOf(0.8D),
                "[]",
                "user",
                "source-hash",
                Map.of("projectId", projectId),
                "");
    }

    private static class InMemoryRepository implements IContextMemoryRepository {

        private final boolean available;
        private final Map<String, ContextMemorySnapshot> snapshots = new LinkedHashMap<>();
        private String failContent;

        private InMemoryRepository(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<ContextMemorySnapshot> search(ContextMemorySearchCriteria criteria) {
            return snapshots.values().stream().limit(criteria.limit()).toList();
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
            if (snapshot.content().equals(failContent)) {
                throw new IllegalStateException("broken persistence");
            }
            snapshots.put(snapshot.memoryId(), snapshot);
        }

        @Override
        public boolean updateStatus(String memoryId, String status) {
            ContextMemorySnapshot before = snapshots.get(memoryId);
            if (before == null) return false;
            snapshots.put(memoryId, new ContextMemorySnapshot(
                    before.id(), before.memoryId(), before.scopeType(), before.scopeId(), before.memoryType(),
                    before.title(), before.summary(), before.content(), before.keywords(), status,
                    before.confidence(), before.sourceType(), before.sourceId(), before.sourceMessageHash(),
                    before.createdBy(), before.createTime(), before.updateTime(), before.expireTime()));
            return true;
        }
    }
}
