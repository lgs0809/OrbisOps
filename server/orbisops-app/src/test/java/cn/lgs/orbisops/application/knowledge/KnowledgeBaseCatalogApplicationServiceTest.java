package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeBaseCatalogApplicationServiceTest {

    @Test
    void mergesStoredCatalogWithTypedRagAggregateFallback() {
        MemoryRepository repository = new MemoryRepository();
        repository.save(entry(KnowledgeScope.GLOBAL, "", "ops-kb", "Ops Runbook", "alice", 1L, 2L));
        KnowledgeBaseCatalogApplicationService service = new KnowledgeBaseCatalogApplicationService(
                repository,
                () -> List.of(
                        new KnowledgeAggregateSnapshot("ops-kb", 4L, 12L, List.of()),
                        new KnowledgeAggregateSnapshot("legacy-kb", 2L, 5L, List.of())),
                projectId -> true);

        List<Map<String, Object>> result = service.listGlobal();

        assertEquals(List.of("ops-kb", "legacy-kb"),
                result.stream().map(item -> String.valueOf(item.get("kbId"))).toList());
        assertEquals("Ops Runbook", result.get(0).get("name"));
        assertEquals(4L, result.get(0).get("documentCount"));
        assertEquals("VECTOR_AGGREGATE", result.get(1).get("sourceType"));
    }

    @Test
    void createReturnsCompleteTypedSnapshotWhenRepositoryCannotReadBackImmediately() {
        IKnowledgeBaseCatalogRepository repository = new IKnowledgeBaseCatalogRepository() {
            @Override public List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId) {
                return List.of();
            }
            @Override public Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key) {
                return Optional.empty();
            }
            @Override public void save(KnowledgeBaseCatalogEntry entry) {
            }
        };
        KnowledgeBaseCatalogApplicationService service = new KnowledgeBaseCatalogApplicationService(
                repository, List::of, projectId -> true);

        Map<String, Object> result = service.createGlobal(mutation(
                supplied("ops-kb"), supplied("Ops Runbook"),
                supplied("Production procedures"), absentStatus(), "alice"));

        assertEquals("Ops Runbook", result.get("name"));
        assertEquals("Production procedures", result.get("description"));
        assertEquals("alice", result.get("createBy"));
    }

    @Test
    void projectCatalogRequiresExistingProject() {
        KnowledgeBaseCatalogApplicationService service = new KnowledgeBaseCatalogApplicationService(
                new MemoryRepository(), List::of, projectId -> false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.listProject("missing-project"));

        assertEquals("项目不存在：missing-project", error.getMessage());
    }

    @Test
    void ensureExistsIsIdempotentAndRequiresAuthenticatedWriter() {
        MemoryRepository repository = new MemoryRepository();
        KnowledgeBaseCatalogApplicationService service = new KnowledgeBaseCatalogApplicationService(
                repository, List::of, projectId -> true);

        service.ensureExists(KnowledgeScope.PROJECT, "project-1", "ops-kb", "alice");
        service.ensureExists(KnowledgeScope.PROJECT, "project-1", "ops-kb", "bob");

        assertEquals(1, repository.entries.size());
        assertEquals("alice", repository.entries.values().iterator().next().createBy());
        assertThrows(IllegalArgumentException.class,
                () -> service.ensureExists(KnowledgeScope.GLOBAL, "", "new-kb", ""));
    }

    @Test
    void updateRejectsTypedIdentityMutation() {
        MemoryRepository repository = new MemoryRepository();
        repository.save(entry(KnowledgeScope.GLOBAL, "", "ops-kb", "Ops", "alice", 0, 0));
        KnowledgeBaseCatalogApplicationService service = new KnowledgeBaseCatalogApplicationService(
                repository, List::of, projectId -> true);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.updateGlobal("ops-kb", mutation(
                        supplied("other-kb"), supplied("Renamed"), absentText(), absentStatus(), "bob")));

        assertEquals("KNOWLEDGE_BASE_ID_IMMUTABLE", failure.getMessage());
    }

    private KnowledgeBaseCatalogCommands.Mutation mutation(
            KnowledgeBaseCatalogCommands.Field<String> id,
            KnowledgeBaseCatalogCommands.Field<String> name,
            KnowledgeBaseCatalogCommands.Field<String> description,
            KnowledgeBaseCatalogCommands.Field<KnowledgeStatus> status,
            String actor) {
        return new KnowledgeBaseCatalogCommands.Mutation(
                id, name, description, status,
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                actor);
    }

    private static <T> KnowledgeBaseCatalogCommands.Field<T> supplied(T value) {
        return KnowledgeBaseCatalogCommands.Field.supplied(value);
    }

    private static KnowledgeBaseCatalogCommands.Field<String> absentText() {
        return KnowledgeBaseCatalogCommands.Field.absent();
    }

    private static KnowledgeBaseCatalogCommands.Field<KnowledgeStatus> absentStatus() {
        return KnowledgeBaseCatalogCommands.Field.absent();
    }

    private static KnowledgeBaseCatalogEntry entry(KnowledgeScope scope,
                                                    String projectId,
                                                    String kbId,
                                                    String name,
                                                    String createBy,
                                                    long documentCount,
                                                    long chunkCount) {
        return new KnowledgeBaseCatalogEntry(
                null,
                new KnowledgeBaseCatalogKey(scope, projectId, kbId),
                name,
                "",
                KnowledgeStatus.ENABLED,
                documentCount,
                chunkCount,
                "DB",
                "",
                createBy,
                "",
                "");
    }

    private static final class MemoryRepository implements IKnowledgeBaseCatalogRepository {
        private final Map<KnowledgeBaseCatalogKey, KnowledgeBaseCatalogEntry> entries = new LinkedHashMap<>();

        @Override
        public List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId) {
            String project = scope == KnowledgeScope.GLOBAL ? "" : projectId;
            return entries.values().stream()
                    .filter(entry -> entry.key().scope() == scope)
                    .filter(entry -> entry.key().projectId().equals(project))
                    .toList();
        }

        @Override public Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key) {
            return Optional.ofNullable(entries.get(key));
        }
        @Override public void save(KnowledgeBaseCatalogEntry entry) {
            entries.put(entry.key(), entry);
        }
    }
}
