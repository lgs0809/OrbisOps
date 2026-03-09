package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeCatalogApplicationService<J, F> {

    private final KnowledgeCatalogPort<J, F> port;
    private final KnowledgeAuditPort auditPort;
    private final KnowledgeBaseCatalogApplicationService catalogService;
    private final KnowledgeRagDocumentApplicationService ragDocumentService;
    private final KnowledgeRetrievalPolicyApplicationService retrievalPolicyService;
    private final KnowledgeAuthorizationApplicationService authorizationService;
    private final KnowledgeProjectDefaultPort projectDefaultPort;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeCatalogApplicationService(
            KnowledgeCatalogPort<J, F> port,
            KnowledgeAuditPort auditPort,
            KnowledgeBaseCatalogApplicationService catalogService,
            KnowledgeRagDocumentApplicationService ragDocumentService,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeAuthorizationApplicationService authorizationService,
            KnowledgeProjectDefaultPort projectDefaultPort) {
        this(port, auditPort, catalogService, ragDocumentService,
                retrievalPolicyService, authorizationService,
                projectDefaultPort, new KnowledgeCatalogPolicy());
    }

    KnowledgeCatalogApplicationService(
            KnowledgeCatalogPort<J, F> port,
            KnowledgeAuditPort auditPort,
            KnowledgeBaseCatalogApplicationService catalogService,
            KnowledgeRagDocumentApplicationService ragDocumentService,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeAuthorizationApplicationService authorizationService,
            KnowledgeProjectDefaultPort projectDefaultPort,
            KnowledgeCatalogPolicy policy) {
        if (port == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("KNOWLEDGE_AUDIT_PORT_REQUIRED");
        if (catalogService == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_CATALOG_SERVICE_REQUIRED");
        if (ragDocumentService == null) throw new IllegalArgumentException("KNOWLEDGE_RAG_DOCUMENT_SERVICE_REQUIRED");
        if (retrievalPolicyService == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_SERVICE_REQUIRED");
        if (authorizationService == null) throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_SERVICE_REQUIRED");
        if (projectDefaultPort == null) throw new IllegalArgumentException("KNOWLEDGE_PROJECT_DEFAULT_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        this.port = port;
        this.auditPort = auditPort;
        this.catalogService = catalogService;
        this.ragDocumentService = ragDocumentService;
        this.retrievalPolicyService = retrievalPolicyService;
        this.authorizationService = authorizationService;
        this.projectDefaultPort = projectDefaultPort;
        this.policy = policy;
    }

    public List<Map<String, Object>> listGlobal() {
        Map<String, Long> usageCounts = authorizationService.enabledUsageCounts();
        return catalogService.listGlobalSnapshots().stream()
                .map(snapshot -> {
                    Map<String, Object> result = new LinkedHashMap<>(snapshot.view(false));
                    result.put("usedProjectCount",
                            usageCounts.getOrDefault(snapshot.knowledgeBaseId(), 0L));
                    return Collections.unmodifiableMap(result);
                })
                .toList();
    }

    public Map<String, Object> getGlobal(String kbId) {
        return catalogService.getGlobal(policy.requiredId(kbId));
    }

    public Map<String, Object> globalStats(String kbId) {
        return ragDocumentService.statistics(
                KnowledgeScope.GLOBAL.name(), "", value(kbId));
    }

    public List<Map<String, Object>> globalUsageProjects(String kbId) {
        String id = policy.requiredId(kbId);
        catalogService.getGlobal(id);
        return authorizationService.usageProjects(id);
    }

    public Map<String, Object> createGlobal(KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String operator = requiredActor(command.actor());
        KnowledgeBaseCatalogEntry created = catalogService.createGlobalEntry(command);
        Map<String, Object> result = catalogService.view(created);
        auditPort.record("", "create-global", created.key().kbId(), null,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> updateGlobal(String kbId,
                                            KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String operator = requiredActor(command.actor());
        String id = policy.requiredId(kbId);
        Map<String, Object> before = catalogService.getGlobal(id);
        Map<String, Object> result = catalogService.updateGlobal(id, command);
        auditPort.record("", "update-global", id, before, audited(result, operator));
        return result;
    }

    public Map<String, Object> updateGlobalStatus(String kbId,
                                                  KnowledgeBaseCatalogCommands.StatusChange command) {
        require(command);
        String operator = requiredActor(command.actor());
        String id = policy.requiredId(kbId);
        Map<String, Object> before = catalogService.getGlobal(id);
        Map<String, Object> result = catalogService.updateGlobalStatus(id, command);
        auditPort.record("", "status-global", id, before, audited(result, operator));
        return result;
    }

    public List<Map<String, Object>> listProject(String projectId) {
        return catalogService.listProject(policy.requiredProject(projectId));
    }

    public Map<String, Object> getProject(String projectId, String kbId) {
        return catalogService.getProject(policy.requiredProject(projectId), policy.requiredId(kbId));
    }

    public Map<String, Object> projectStats(String projectId, String kbId) {
        return ragDocumentService.statistics(
                KnowledgeScope.PROJECT.name(),
                policy.requiredProject(projectId),
                value(kbId));
    }

    public Map<String, Object> createProject(String projectId,
                                             KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String operator = requiredActor(command.actor());
        String project = policy.requiredProject(projectId);
        KnowledgeBaseCatalogEntry created = catalogService.createProjectEntry(project, command);
        Map<String, Object> result = catalogService.view(created);
        auditPort.record(project, "create-project", created.key().kbId(), null,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> updateProject(String projectId,
                                             String kbId,
                                             KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String operator = requiredActor(command.actor());
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        Map<String, Object> before = catalogService.getProject(project, id);
        Map<String, Object> result = catalogService.updateProject(project, id, command);
        auditPort.record(project, "update-project", id, before, audited(result, operator));
        return result;
    }

    public Map<String, Object> updateProjectStatus(String projectId,
                                                   String kbId,
                                                   KnowledgeBaseCatalogCommands.StatusChange command) {
        require(command);
        String operator = requiredActor(command.actor());
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        Map<String, Object> before = catalogService.getProject(project, id);
        Map<String, Object> result = catalogService.updateProjectStatus(project, id, command);
        auditPort.record(project, "status-project", id, before, audited(result, operator));
        return result;
    }

    public List<Map<String, Object>> listAuthorized(String projectId) {
        String project = policy.requiredProject(projectId);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (KnowledgeBaseCatalogSnapshot snapshot : catalogService.listProjectSnapshots(project)) {
            result.put(snapshot.knowledgeBaseId(), snapshot.view(false));
        }
        for (String kbId : authorizationService.enabledKnowledgeBaseIds(project)) {
            try {
                KnowledgeBaseCatalogSnapshot snapshot = catalogService.globalSnapshot(kbId);
                if (snapshot.status() != KnowledgeStatus.ENABLED) continue;
                Map<String, Object> item = new LinkedHashMap<>(snapshot.view(false));
                item.put("bindingStatus", KnowledgeStatus.ENABLED.name());
                result.putIfAbsent(snapshot.knowledgeBaseId(), Collections.unmodifiableMap(item));
            } catch (RuntimeException ignored) {
                // Stale authorization rows behave like a SQL inner join miss.
            }
        }
        String legacyDefault = value(projectDefaultPort.defaultKnowledgeBaseId(project));
        if (!legacyDefault.isBlank()) {
            result.putIfAbsent(legacyDefault, legacyDefaultView(legacyDefault));
        }
        return List.copyOf(result.values());
    }

    public Map<String, Object> enableGlobalForProject(KnowledgeGlobalAuthorizationCommand command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_COMMAND_REQUIRED");
        String operator = requiredActor(command.enabledBy());
        String project = policy.requiredProject(command.projectId());
        String id = policy.requiredId(command.globalKbId());
        catalogService.globalSnapshot(id);
        Map<String, Object> global = new LinkedHashMap<>(
                catalogService.materializeGlobal(id, operator));
        ProjectKnowledgeAuthorization authorization = authorizationService.enable(command);
        global.put("projectId", project);
        global.put("bindingStatus", authorization.status().name());
        global.put("enabledBy", authorization.enabledBy());
        global.put("enabledTime", authorization.enabledAt());
        global.put("updateTime", authorization.updatedAt());
        auditPort.record(project, "enable-global", id, Map.of("globalKbId", id),
                audited(global, operator));
        return global;
    }

    public J importGlobalDocuments(String kbId,
                                   String name,
                                   List<F> files,
                                   String actor) {
        String operator = requiredActor(actor);
        String id = policy.requiredId(kbId);
        catalogService.getGlobal(id);
        J result = port.importGlobalDocuments(
                id, required(name, "KNOWLEDGE_IMPORT_NAME_REQUIRED"), files(files));
        auditPort.record("", "import-global-documents", id, null, audited(result, operator));
        return result;
    }

    public J importProjectDocuments(String projectId,
                                    String kbId,
                                    String name,
                                    List<F> files,
                                    String actor) {
        String operator = requiredActor(actor);
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        catalogService.getProject(project, id);
        J result = port.importProjectDocuments(project, id,
                required(name, "KNOWLEDGE_IMPORT_NAME_REQUIRED"), files(files));
        auditPort.record(project, "import-project-documents", id, null,
                audited(result, operator));
        return result;
    }

    public List<Map<String, Object>> listGlobalDocuments(String kbId) {
        return ragDocumentService.list(
                KnowledgeScope.GLOBAL.name(), "", policy.requiredId(kbId), 500);
    }

    public List<Map<String, Object>> listProjectDocuments(String projectId, String kbId) {
        return ragDocumentService.list(
                KnowledgeScope.PROJECT.name(),
                policy.requiredProject(projectId),
                policy.requiredId(kbId),
                500);
    }

    public List<Map<String, Object>> listGlobalChunks(String kbId, int limit) {
        return ragDocumentService.list(
                KnowledgeScope.GLOBAL.name(), "", policy.requiredId(kbId), limit(limit));
    }

    public List<Map<String, Object>> listProjectChunks(String projectId, String kbId, int limit) {
        return ragDocumentService.list(
                KnowledgeScope.PROJECT.name(),
                policy.requiredProject(projectId),
                policy.requiredId(kbId),
                limit(limit));
    }

    public boolean deleteGlobalChunk(String kbId, String chunkId, String actor) {
        String operator = requiredActor(actor);
        String id = policy.requiredId(kbId);
        String chunk = policy.requiredChunk(chunkId);
        boolean result = ragDocumentService.deleteChunk(
                KnowledgeScope.GLOBAL.name(), "", id, chunk);
        if (result) {
            auditPort.record("", "delete-global-chunk", id, null,
                    audited(Map.of("chunkId", chunk), operator));
        }
        return result;
    }

    public boolean deleteProjectChunk(String projectId,
                                      String kbId,
                                      String chunkId,
                                      String actor) {
        String operator = requiredActor(actor);
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        String chunk = policy.requiredChunk(chunkId);
        boolean result = ragDocumentService.deleteChunk(
                KnowledgeScope.PROJECT.name(), project, id, chunk);
        if (result) {
            auditPort.record(project, "delete-project-chunk", id, null,
                    audited(Map.of("chunkId", chunk), operator));
        }
        return result;
    }

    public Map<String, Object> deleteGlobalChunks(String kbId, String actor) {
        String operator = requiredActor(actor);
        String id = policy.requiredId(kbId);
        Map<String, Object> result = ragDocumentService.deleteChunks(
                KnowledgeScope.GLOBAL.name(), "", id);
        auditPort.record("", "delete-global-chunks", id, null, audited(result, operator));
        return result;
    }

    public Map<String, Object> deleteProjectChunks(String projectId,
                                                   String kbId,
                                                   String actor) {
        String operator = requiredActor(actor);
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        Map<String, Object> result = ragDocumentService.deleteChunks(
                KnowledgeScope.PROJECT.name(), project, id);
        auditPort.record(project, "delete-project-chunks", id, null, audited(result, operator));
        return result;
    }

    public Map<String, Object> globalRetrievalPolicy(String kbId) {
        return retrievalPolicyService.get(
                KnowledgeScope.GLOBAL.name(), "", policy.requiredId(kbId));
    }

    public Map<String, Object> projectRetrievalPolicy(String projectId, String kbId) {
        return retrievalPolicyService.get(
                KnowledgeScope.PROJECT.name(),
                policy.requiredProject(projectId), policy.requiredId(kbId));
    }

    public Map<String, Object> updateGlobalRetrievalPolicy(String kbId,
                                                           KnowledgeRetrievalPolicyCommand command) {
        require(command);
        String operator = requiredActor(command.actor());
        String id = policy.requiredId(kbId);
        Map<String, Object> before = globalRetrievalPolicy(id);
        Map<String, Object> result = retrievalPolicyService.update(
                KnowledgeScope.GLOBAL.name(), "", id, command);
        auditPort.record("", "update-global-retrieval-policy", id, before,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> updateProjectRetrievalPolicy(String projectId,
                                                            String kbId,
                                                            KnowledgeRetrievalPolicyCommand command) {
        require(command);
        String operator = requiredActor(command.actor());
        String project = policy.requiredProject(projectId);
        String id = policy.requiredId(kbId);
        Map<String, Object> before = projectRetrievalPolicy(project, id);
        Map<String, Object> result = retrievalPolicyService.update(
                KnowledgeScope.PROJECT.name(), project, id, command);
        auditPort.record(project, "update-project-retrieval-policy", id, before,
                audited(result, operator));
        return result;
    }

    private Map<String, Object> legacyDefaultView(String kbId) {
        Map<String, Object> result;
        try {
            Map<String, Object> stored = catalogService.getGlobal(kbId);
            result = stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
        } catch (RuntimeException ignored) {
            result = new LinkedHashMap<>();
        }
        result.putIfAbsent("kbId", kbId);
        result.putIfAbsent("knowledgeTag", kbId);
        result.putIfAbsent("name", kbId);
        result.putIfAbsent("kbName", kbId);
        result.putIfAbsent("scope", "PROJECT_LEGACY_DEFAULT");
        result.putIfAbsent("status", "ENABLED");
        result.putIfAbsent("statusCode", 1);
        result.putIfAbsent("documentCount", 0L);
        result.putIfAbsent("chunkCount", 0L);
        result.putIfAbsent("sourceType", "PROJECT_DEFAULT_FIELD");
        result.put("projectBinding", "DEFAULT_KNOWLEDGE_BASE_ID");
        return result;
    }

    private Map<String, Object> audited(Object result, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("actor", actor);
        return payload;
    }

    private List<F> files(List<F> input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("KNOWLEDGE_IMPORT_FILES_REQUIRED");
        return List.copyOf(input);
    }

    private int limit(int value) {
        return Math.max(1, Math.min(value, 500));
    }

    private void require(KnowledgeBaseCatalogCommands.Mutation command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_COMMAND_REQUIRED");
    }

    private void require(KnowledgeBaseCatalogCommands.StatusChange command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_COMMAND_REQUIRED");
    }

    private void require(KnowledgeRetrievalPolicyCommand command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_COMMAND_REQUIRED");
    }

    private String requiredActor(String actor) {
        return required(actor, "KNOWLEDGE_ACTOR_REQUIRED");
    }

    private String required(String input, String error) {
        String result = value(input);
        if (result.isBlank()) throw new IllegalArgumentException(error);
        return result;
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }
}
