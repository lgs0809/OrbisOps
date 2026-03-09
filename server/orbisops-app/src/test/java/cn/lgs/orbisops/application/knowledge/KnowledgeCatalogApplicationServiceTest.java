package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeDocumentCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeRetrievalPolicyRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IProjectKnowledgeAuthorizationRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeAuthorizationUsageCount;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyState;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorizationUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeCatalogApplicationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void createGlobalUsesTypedAuthenticatedActorAndAuditsPrincipal() {
        KnowledgeCatalogPort<Object, Object> port = mock(KnowledgeCatalogPort.class);
        KnowledgeAuditPort auditPort = mock(KnowledgeAuditPort.class);
        RecordingCatalogRepository catalogRepository = new RecordingCatalogRepository();
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                port, auditPort, catalogRepository, new RecordingAuthorizationRepository(), projectId -> "");

        Map<String, Object> result = service.createGlobal(new KnowledgeBaseCatalogCommands.Mutation(
                KnowledgeBaseCatalogCommands.Field.supplied("global-kb"),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                KnowledgeBaseCatalogCommands.Field.absent(),
                "alice"));

        assertEquals("global-kb", result.get("kbId"));
        assertEquals("alice", catalogRepository.entries.values().iterator().next().createBy());
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq(""),
                eq("create-global"),
                eq("global-kb"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enableGlobalForProjectUsesAuthenticatedPrincipalAsEnabledBy() {
        KnowledgeCatalogPort<Object, Object> port = mock(KnowledgeCatalogPort.class);
        KnowledgeAuditPort auditPort = mock(KnowledgeAuditPort.class);
        RecordingCatalogRepository catalogRepository = new RecordingCatalogRepository();
        catalogRepository.save(entry(KnowledgeScope.GLOBAL, "", "global-kb", "system"));
        RecordingAuthorizationRepository authorizationRepository = new RecordingAuthorizationRepository();
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                port, auditPort, catalogRepository, authorizationRepository, projectId -> "");

        Map<String, Object> result = service.enableGlobalForProject(
                new KnowledgeGlobalAuthorizationCommand(
                        "project-1", "global-kb", KnowledgeStatus.ENABLED, "alice"));

        assertEquals("alice", authorizationRepository.saved.enabledBy());
        assertEquals("ENABLED", result.get("bindingStatus"));
        assertEquals("project-1", result.get("projectId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteGlobalChunkAuditsAuthenticatedPrincipal() {
        KnowledgeCatalogPort<Object, Object> port = mock(KnowledgeCatalogPort.class);
        KnowledgeAuditPort auditPort = mock(KnowledgeAuditPort.class);
        KnowledgeRagDocumentPort ragDocumentPort = mock(KnowledgeRagDocumentPort.class);
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                port, auditPort, new RecordingCatalogRepository(),
                new RecordingAuthorizationRepository(), projectId -> "", ragDocumentPort);
        when(ragDocumentPort.deleteChunk("chunk-1", "global-kb", "GLOBAL", ""))
                .thenReturn(true);

        service.deleteGlobalChunk("global-kb", "chunk-1", "alice");

        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq(""),
                eq("delete-global-chunk"),
                eq("global-kb"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
        assertEquals(Map.of("chunkId", "chunk-1"),
                ((Map<?, ?>) auditAfter.getValue()).get("result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void importGlobalDocumentsRejectsMissingActorBeforePortAccess() {
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                mock(KnowledgeCatalogPort.class),
                mock(KnowledgeAuditPort.class),
                new RecordingCatalogRepository(),
                new RecordingAuthorizationRepository(),
                projectId -> "");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.importGlobalDocuments(
                        "global-kb", "batch", List.of(new Object()), " "));

        assertEquals("KNOWLEDGE_ACTOR_REQUIRED", error.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listGlobalEnrichesAuthorizationUsageCountsInApplication() {
        RecordingCatalogRepository catalogRepository = new RecordingCatalogRepository();
        catalogRepository.save(entry(KnowledgeScope.GLOBAL, "", "global-a", "alice"));
        catalogRepository.save(entry(KnowledgeScope.GLOBAL, "", "global-b", "alice"));
        RecordingAuthorizationRepository authorizationRepository = new RecordingAuthorizationRepository();
        authorizationRepository.counts = List.of(new KnowledgeAuthorizationUsageCount("global-a", 3L));
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                mock(KnowledgeCatalogPort.class),
                mock(KnowledgeAuditPort.class),
                catalogRepository,
                authorizationRepository,
                projectId -> "");

        List<Map<String, Object>> result = service.listGlobal();

        assertEquals(3L, result.get(0).get("usedProjectCount"));
        assertEquals(0L, result.get(1).get("usedProjectCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuthorizedCombinesProjectGlobalAndLegacyDefaultKnowledgeBases() {
        RecordingCatalogRepository catalogRepository = new RecordingCatalogRepository();
        catalogRepository.save(entry(KnowledgeScope.PROJECT, "project-1", "project-kb", "alice"));
        catalogRepository.save(entry(KnowledgeScope.GLOBAL, "", "global-a", "alice"));
        RecordingAuthorizationRepository authorizationRepository = new RecordingAuthorizationRepository();
        authorizationRepository.enabledIds = List.of("global-a");
        KnowledgeCatalogApplicationService<Object, Object> service = service(
                mock(KnowledgeCatalogPort.class),
                mock(KnowledgeAuditPort.class),
                catalogRepository,
                authorizationRepository,
                projectId -> "legacy-kb");

        List<Map<String, Object>> result = service.listAuthorized("project-1");

        assertEquals(List.of("project-kb", "global-a", "legacy-kb"),
                result.stream().map(item -> String.valueOf(item.get("kbId"))).toList());
        assertEquals("DEFAULT_KNOWLEDGE_BASE_ID",
                result.get(2).get("projectBinding"));
    }

    private KnowledgeCatalogApplicationService<Object, Object> service(
            KnowledgeCatalogPort<Object, Object> port,
            KnowledgeAuditPort auditPort,
            RecordingCatalogRepository catalogRepository,
            IProjectKnowledgeAuthorizationRepository authorizationRepository,
            KnowledgeProjectDefaultPort projectDefaultPort) {
        return service(
                port, auditPort, catalogRepository, authorizationRepository,
                projectDefaultPort, mock(KnowledgeRagDocumentPort.class));
    }

    private KnowledgeCatalogApplicationService<Object, Object> service(
            KnowledgeCatalogPort<Object, Object> port,
            KnowledgeAuditPort auditPort,
            RecordingCatalogRepository catalogRepository,
            IProjectKnowledgeAuthorizationRepository authorizationRepository,
            KnowledgeProjectDefaultPort projectDefaultPort,
            KnowledgeRagDocumentPort ragDocumentPort) {
        KnowledgeBaseCatalogApplicationService catalogService = new KnowledgeBaseCatalogApplicationService(
                catalogRepository, List::of, projectId -> true);
        KnowledgeDocumentCatalogApplicationService documentCatalogService =
                new KnowledgeDocumentCatalogApplicationService(
                        mock(IKnowledgeDocumentCatalogRepository.class));
        KnowledgeRagDocumentApplicationService ragDocumentService =
                new KnowledgeRagDocumentApplicationService(
                        ragDocumentPort, documentCatalogService, projectId -> true);
        return new KnowledgeCatalogApplicationService<>(
                port,
                auditPort,
                catalogService,
                ragDocumentService,
                retrievalPolicyService(catalogService),
                new KnowledgeAuthorizationApplicationService(authorizationRepository),
                projectDefaultPort);
    }

    private KnowledgeRetrievalPolicyApplicationService retrievalPolicyService(
            KnowledgeBaseCatalogApplicationService catalogService) {
        IKnowledgeRetrievalPolicyRepository repository = new IKnowledgeRetrievalPolicyRepository() {
            @Override
            public Optional<KnowledgeRetrievalPolicyState> find(KnowledgeRetrievalPolicyKey key) {
                return Optional.empty();
            }

            @Override
            public KnowledgeRetrievalPolicyState save(KnowledgeRetrievalPolicyKey key,
                                                      KnowledgeRetrievalPolicy policy) {
                return KnowledgeRetrievalPolicyState.transientState(key, policy);
            }
        };
        return new KnowledgeRetrievalPolicyApplicationService(repository, catalogService);
    }

    private static KnowledgeBaseCatalogEntry entry(KnowledgeScope scope,
                                                   String projectId,
                                                   String kbId,
                                                   String createBy) {
        return new KnowledgeBaseCatalogEntry(
                null,
                new KnowledgeBaseCatalogKey(scope, projectId, kbId),
                kbId,
                "",
                KnowledgeStatus.ENABLED,
                0L,
                0L,
                "DB",
                "",
                createBy,
                "",
                "");
    }

    private static final class RecordingCatalogRepository implements IKnowledgeBaseCatalogRepository {
        private final Map<KnowledgeBaseCatalogKey, KnowledgeBaseCatalogEntry> entries = new LinkedHashMap<>();

        @Override
        public List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId) {
            String project = scope == KnowledgeScope.GLOBAL ? "" : projectId;
            return entries.values().stream()
                    .filter(entry -> entry.key().scope() == scope)
                    .filter(entry -> entry.key().projectId().equals(project))
                    .toList();
        }

        @Override
        public Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key) {
            return Optional.ofNullable(entries.get(key));
        }

        @Override
        public void save(KnowledgeBaseCatalogEntry entry) {
            entries.put(entry.key(), entry);
        }
    }

    private static final class RecordingAuthorizationRepository
            implements IProjectKnowledgeAuthorizationRepository {
        private ProjectKnowledgeAuthorization saved;
        private List<String> enabledIds = List.of();
        private List<ProjectKnowledgeAuthorizationUsage> usages = List.of();
        private List<KnowledgeAuthorizationUsageCount> counts = List.of();

        @Override
        public ProjectKnowledgeAuthorization save(ProjectKnowledgeAuthorization authorization) {
            this.saved = authorization;
            return authorization;
        }

        @Override
        public List<String> listEnabledKnowledgeBaseIds(String projectId) {
            return enabledIds;
        }

        @Override
        public List<ProjectKnowledgeAuthorizationUsage> listUsageProjects(String globalKbId) {
            return usages;
        }

        @Override
        public List<KnowledgeAuthorizationUsageCount> listEnabledUsageCounts() {
            return counts;
        }
    }
}
