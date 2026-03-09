package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
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

class ContextMemoryAdminApplicationServiceTest {

    @Test
    void createGeneratesIdentityNormalizesDefinitionAndClampsConfidence() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "generated-1");

        ContextMemorySnapshot created = service.create(command(
                null,
                " project ",
                " demo-project ",
                " project_context ",
                " DDD migration ",
                " summary ",
                " content ",
                true,
                BigDecimal.valueOf(2D)));

        assertEquals("ctx-mem-generated-1", created.memoryId());
        assertEquals("PROJECT", created.scopeType());
        assertEquals("demo-project", created.scopeId());
        assertEquals("PROJECT_CONTEXT", created.memoryType());
        assertEquals("DDD migration", created.title());
        assertEquals(BigDecimal.ONE, created.confidence());
        assertEquals("ACTIVE", created.status());
    }

    @Test
    void explicitIdentityIsPreserved() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "unused");

        ContextMemorySnapshot created = service.create(command(
                " ctx-explicit ",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                false,
                null));

        assertEquals("ctx-explicit", created.memoryId());
        assertEquals(BigDecimal.valueOf(0.8D), created.confidence());
    }

    @Test
    void partialUpdatePreservesAbsentFieldsAndChangesProvidedFields() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "unused");
        service.create(command(
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                true,
                BigDecimal.valueOf(0.7D)));

        ContextMemorySnapshot updated = service.update("ctx-1", new ContextMemoryMutationCommand(
                null,
                null,
                null,
                null,
                null,
                "new summary",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null));

        assertEquals("PROJECT", updated.scopeType());
        assertEquals("demo-project", updated.scopeId());
        assertEquals("title", updated.title());
        assertEquals("new summary", updated.summary());
        assertEquals("content", updated.content());
        assertEquals(BigDecimal.valueOf(0.7D), updated.confidence());
    }

    @Test
    void presentInvalidConfidenceUsesHistoricalDefaultWhileAbsentPreserves() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "unused");
        service.create(command(
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                true,
                BigDecimal.valueOf(0.6D)));

        ContextMemorySnapshot defaulted = service.update("ctx-1", new ContextMemoryMutationCommand(
                null, null, null, null, null, null, null, null, null,
                true, null, null, null, null, null));
        ContextMemorySnapshot preserved = service.update("ctx-1", new ContextMemoryMutationCommand(
                null, null, null, null, null, "again", null, null, null,
                false, null, null, null, null, null));

        assertEquals(BigDecimal.valueOf(0.8D), defaulted.confidence());
        assertEquals(BigDecimal.valueOf(0.8D), preserved.confidence());
    }

    @Test
    void updateStatusNormalizesAndValidatesIdentity() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "unused");
        service.create(command(
                "ctx-1", "PROJECT", "demo-project", "PROJECT_CONTEXT",
                "title", "summary", "content", false, null));

        ContextMemorySnapshot updated = service.updateStatus(" ctx-1 ", " archived ");

        assertEquals("ARCHIVED", updated.status());
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(" ", "ACTIVE"));
    }

    @Test
    void explicitBlankRequiredFieldIsRejectedDuringPartialUpdate() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService service = service(repository, () -> "unused");
        service.create(command(
                "ctx-1", "PROJECT", "demo-project", "PROJECT_CONTEXT",
                "title", "summary", "content", false, null));

        assertThrows(IllegalArgumentException.class, () -> service.update("ctx-1",
                new ContextMemoryMutationCommand(
                        null, null, null, null, " ", null, null, null, null,
                        false, null, null, null, null, null)));
    }

    @Test
    void identityGeneratorAndRequiredCommandFailFast() {
        InMemoryRepository repository = new InMemoryRepository();
        ContextMemoryAdminApplicationService missingIdentity = service(repository, () -> " ");

        assertThrows(IllegalStateException.class, () -> missingIdentity.create(command(
                null, "PROJECT", "demo-project", "PROJECT_CONTEXT",
                "title", "summary", "content", false, null)));
        assertThrows(IllegalArgumentException.class, () -> missingIdentity.create(null));
    }

    private ContextMemoryAdminApplicationService service(InMemoryRepository repository,
                                                         java.util.function.Supplier<String> identitySupplier) {
        ContextMemoryStoreApplicationService storeService = new ContextMemoryStoreApplicationService(
                repository,
                new ContextMemoryProjectionPolicy(),
                null,
                null);
        return new ContextMemoryAdminApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy(),
                identitySupplier);
    }

    private ContextMemoryMutationCommand command(String memoryId,
                                                 String scopeType,
                                                 String scopeId,
                                                 String memoryType,
                                                 String title,
                                                 String summary,
                                                 String content,
                                                 boolean confidencePresent,
                                                 BigDecimal confidence) {
        return new ContextMemoryMutationCommand(
                memoryId,
                scopeType,
                scopeId,
                memoryType,
                title,
                summary,
                content,
                "[]",
                null,
                confidencePresent,
                confidence,
                "manual",
                "session-1",
                "source-hash",
                "user-1");
    }

    private static class InMemoryRepository implements IContextMemoryRepository {

        private final Map<String, ContextMemorySnapshot> snapshots = new LinkedHashMap<>();

        @Override
        public boolean available() {
            return true;
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
