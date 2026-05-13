package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeRetrievalPolicyRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyState;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeRetrievalPolicyApplicationServiceTest {

    @Test
    void returnsDefaultPolicyWhenRepositoryHasNoOverride() {
        List<String> events = new ArrayList<>();
        RecordingPolicyRepository repository = new RecordingPolicyRepository(events);
        KnowledgeRetrievalPolicyApplicationService service = new KnowledgeRetrievalPolicyApplicationService(
                repository, catalogService(events));

        Map<String, Object> result = service.get("GLOBAL", "ignored", "ops-kb");

        assertEquals("GLOBAL", result.get("scope"));
        assertEquals("", result.get("projectId"));
        assertEquals(3000, result.get("maxSegmentChars"));
        assertEquals(0, result.get("hardSplitOverlapChars"));
        assertEquals(5, result.get("topK"));
        assertEquals("STRUCTURED_RAG", result.get("vectorStatus"));
        assertEquals(List.of("policy-find:GLOBAL:ops-kb"), events);
    }

    @Test
    void materializesOwnerBeforeSavingNormalizedProjectPolicy() {
        List<String> events = new ArrayList<>();
        RecordingPolicyRepository repository = new RecordingPolicyRepository(events);
        KnowledgeRetrievalPolicyApplicationService service = new KnowledgeRetrievalPolicyApplicationService(
                repository, catalogService(events));

        Map<String, Object> result = service.update(
                "PROJECT",
                " project-1 ",
                " ops-kb ",
                new KnowledgeRetrievalPolicyCommand(
                        4096,
                        256,
                        8,
                        true,
                        "embed-v2",
                        "{\"department\":\"ops\"}",
                        "alice"));

        assertEquals(4096, result.get("chunkSize"));
        assertEquals(256, result.get("overlapSize"));
        assertEquals(8, result.get("topK"));
        assertEquals(true, result.get("rerankEnabled"));
        assertEquals(List.of(
                "catalog-find:PROJECT:project-1:ops-kb",
                "catalog-save:PROJECT:project-1:ops-kb",
                "policy-save:PROJECT:project-1:ops-kb"), events);
    }

    @Test
    void rejectsProjectPolicyWithoutProjectId() {
        List<String> events = new ArrayList<>();
        KnowledgeRetrievalPolicyApplicationService service = new KnowledgeRetrievalPolicyApplicationService(
                new RecordingPolicyRepository(events), catalogService(events));

        assertThrows(IllegalArgumentException.class,
                () -> service.get("PROJECT", "", "ops-kb"));
    }

    private KnowledgeBaseCatalogApplicationService catalogService(List<String> events) {
        IKnowledgeBaseCatalogRepository repository = new IKnowledgeBaseCatalogRepository() {
            private final Map<KnowledgeBaseCatalogKey, KnowledgeBaseCatalogEntry> entries = new LinkedHashMap<>();

            @Override
            public List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId) {
                return entries.values().stream()
                        .filter(entry -> entry.key().scope() == scope)
                        .filter(entry -> entry.key().projectId().equals(projectId == null ? "" : projectId.trim()))
                        .toList();
            }

            @Override
            public Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key) {
                events.add("catalog-find:" + key.scope() + ":" + key.projectId() + ":" + key.kbId());
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public void save(KnowledgeBaseCatalogEntry entry) {
                events.add("catalog-save:" + entry.key().scope() + ":"
                        + entry.key().projectId() + ":" + entry.key().kbId());
                entries.put(entry.key(), entry);
            }
        };
        return new KnowledgeBaseCatalogApplicationService(repository, List::of, projectId -> true);
    }

    private static final class RecordingPolicyRepository implements IKnowledgeRetrievalPolicyRepository {
        private final List<String> events;
        private KnowledgeRetrievalPolicyState state;

        private RecordingPolicyRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public Optional<KnowledgeRetrievalPolicyState> find(KnowledgeRetrievalPolicyKey key) {
            events.add("policy-find:" + key.scope() + ":" + key.kbId());
            return Optional.ofNullable(state);
        }

        @Override
        public KnowledgeRetrievalPolicyState save(KnowledgeRetrievalPolicyKey key,
                                                  KnowledgeRetrievalPolicy policy) {
            events.add("policy-save:" + key.scope() + ":" + key.projectId() + ":" + key.kbId());
            state = new KnowledgeRetrievalPolicyState(7L, key, policy, "2026-07-19 08:00:00");
            return state;
        }
    }
}
