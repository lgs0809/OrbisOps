package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KnowledgeBaseCatalogApplicationService {

    private final IKnowledgeBaseCatalogRepository repository;
    private final KnowledgeAggregateCatalogPort aggregatePort;
    private final KnowledgeProjectExistencePort projectExistencePort;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeBaseCatalogApplicationService(
            IKnowledgeBaseCatalogRepository repository,
            KnowledgeAggregateCatalogPort aggregatePort,
            KnowledgeProjectExistencePort projectExistencePort) {
        this(repository, aggregatePort, projectExistencePort, new KnowledgeCatalogPolicy());
    }

    KnowledgeBaseCatalogApplicationService(
            IKnowledgeBaseCatalogRepository repository,
            KnowledgeAggregateCatalogPort aggregatePort,
            KnowledgeProjectExistencePort projectExistencePort,
            KnowledgeCatalogPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_CATALOG_REPOSITORY_REQUIRED");
        if (aggregatePort == null) throw new IllegalArgumentException("KNOWLEDGE_AGGREGATE_CATALOG_PORT_REQUIRED");
        if (projectExistencePort == null) throw new IllegalArgumentException("KNOWLEDGE_PROJECT_EXISTENCE_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        this.repository = repository;
        this.aggregatePort = aggregatePort;
        this.projectExistencePort = projectExistencePort;
        this.policy = policy;
    }

    public List<Map<String, Object>> listGlobal() {
        return listGlobalSnapshots().stream()
                .map(snapshot -> snapshot.view(false))
                .toList();
    }

    public List<KnowledgeBaseCatalogSnapshot> listGlobalSnapshots() {
        Map<String, KnowledgeBaseCatalogSnapshot> result = new LinkedHashMap<>();
        for (KnowledgeBaseCatalogEntry entry : safe(repository.list(KnowledgeScope.GLOBAL, ""))) {
            result.put(entry.key().kbId(), KnowledgeBaseCatalogSnapshot.from(entry));
        }
        for (KnowledgeAggregateSnapshot aggregate : aggregates()) {
            result.compute(aggregate.knowledgeBaseId(), (ignored, stored) -> stored == null
                    ? KnowledgeBaseCatalogSnapshot.from(aggregate)
                    : stored.withAggregateCounts(aggregate));
        }
        return List.copyOf(result.values());
    }

    public Map<String, Object> getGlobal(String kbId) {
        return globalSnapshot(kbId).view(true);
    }

    public KnowledgeBaseCatalogSnapshot globalSnapshot(String kbId) {
        String id = policy.requiredId(kbId);
        Optional<KnowledgeBaseCatalogEntry> stored = repository.find(key(KnowledgeScope.GLOBAL, "", id));
        if (stored.isPresent()) return KnowledgeBaseCatalogSnapshot.from(stored.get());
        return aggregate(id)
                .map(KnowledgeBaseCatalogSnapshot::from)
                .orElseThrow(() -> new IllegalArgumentException("通用知识库不存在：" + id));
    }

    public Map<String, Object> createGlobal(KnowledgeBaseCatalogCommands.Mutation command) {
        return view(createGlobalEntry(command), true);
    }

    public KnowledgeBaseCatalogEntry createGlobalEntry(
            KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String kbId = requiredMutationId(command);
        KnowledgeBaseCatalogKey key = key(KnowledgeScope.GLOBAL, "", kbId);
        if (repository.find(key).isPresent()) {
            throw new IllegalArgumentException("通用知识库已存在：" + kbId);
        }
        KnowledgeBaseCatalogEntry candidate = entry(key, command, null);
        return saveAndLoad(policy.create(candidate));
    }

    public Map<String, Object> updateGlobal(String kbId,
                                            KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogKey currentKey = key(KnowledgeScope.GLOBAL, "", id);
        KnowledgeBaseCatalogEntry current = repository.find(currentKey)
                .orElseGet(() -> materializedEntry(id, command.actor()));
        KnowledgeBaseCatalogKey candidateKey = key(
                KnowledgeScope.GLOBAL, "", command.knowledgeBaseId().orElse(id));
        KnowledgeBaseCatalogEntry candidate = entry(candidateKey, command, current);
        return saveAndView(policy.update(current, candidate));
    }

    public Map<String, Object> updateGlobalStatus(String kbId,
                                                  KnowledgeBaseCatalogCommands.StatusChange command) {
        require(command);
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogKey key = key(KnowledgeScope.GLOBAL, "", id);
        KnowledgeBaseCatalogEntry current = repository.find(key)
                .orElseGet(() -> materializedEntry(id, command.actor()));
        KnowledgeBaseCatalogEntry candidate = withStatus(current, policy.status(command.status()));
        return saveAndView(policy.update(current, candidate));
    }

    public Map<String, Object> materializeGlobal(String kbId, String actor) {
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogKey key = key(KnowledgeScope.GLOBAL, "", id);
        Optional<KnowledgeBaseCatalogEntry> stored = repository.find(key);
        if (stored.isPresent()) return view(stored.get(), true);
        return saveAndView(policy.create(materializedEntry(id, requiredActor(actor))));
    }

    public void ensureExists(KnowledgeScope scope,
                             String projectId,
                             String kbId,
                             String actor) {
        if (scope == null) throw new IllegalArgumentException("KNOWLEDGE_SCOPE_REQUIRED");
        String project = scope == KnowledgeScope.PROJECT ? requiredProject(projectId) : "";
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogKey key = key(scope, project, id);
        if (repository.find(key).isPresent()) return;
        KnowledgeBaseCatalogEntry candidate = new KnowledgeBaseCatalogEntry(
                null,
                key,
                id,
                scope == KnowledgeScope.PROJECT ? "项目知识库" : "通用知识库",
                KnowledgeStatus.ENABLED,
                0L,
                0L,
                "DB",
                "",
                requiredActor(actor),
                "",
                "");
        save(policy.create(candidate));
    }

    public List<Map<String, Object>> listProject(String projectId) {
        return listProjectSnapshots(projectId).stream()
                .map(snapshot -> snapshot.view(false))
                .toList();
    }

    public List<KnowledgeBaseCatalogSnapshot> listProjectSnapshots(String projectId) {
        String project = requiredProject(projectId);
        return safe(repository.list(KnowledgeScope.PROJECT, project)).stream()
                .map(KnowledgeBaseCatalogSnapshot::from)
                .toList();
    }

    public Map<String, Object> getProject(String projectId, String kbId) {
        return projectSnapshot(projectId, kbId).view(true);
    }

    public KnowledgeBaseCatalogSnapshot projectSnapshot(String projectId, String kbId) {
        String project = requiredProject(projectId);
        String id = policy.requiredId(kbId);
        return repository.find(key(KnowledgeScope.PROJECT, project, id))
                .map(KnowledgeBaseCatalogSnapshot::from)
                .orElseThrow(() -> new IllegalArgumentException("项目知识库不存在：" + id));
    }

    public Map<String, Object> createProject(String projectId,
                                             KnowledgeBaseCatalogCommands.Mutation command) {
        return view(createProjectEntry(projectId, command), true);
    }

    public KnowledgeBaseCatalogEntry createProjectEntry(
            String projectId,
            KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String project = requiredProject(projectId);
        String kbId = requiredMutationId(command);
        KnowledgeBaseCatalogKey key = key(KnowledgeScope.PROJECT, project, kbId);
        if (repository.find(key).isPresent()) {
            throw new IllegalArgumentException("项目知识库已存在：" + kbId);
        }
        return saveAndLoad(policy.create(entry(key, command, null)));
    }

    public Map<String, Object> updateProject(String projectId,
                                             String kbId,
                                             KnowledgeBaseCatalogCommands.Mutation command) {
        require(command);
        String project = requiredProject(projectId);
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogEntry current = repository.find(key(KnowledgeScope.PROJECT, project, id))
                .orElseThrow(() -> new IllegalArgumentException("项目知识库不存在：" + id));
        KnowledgeBaseCatalogKey candidateKey = key(
                KnowledgeScope.PROJECT, project, command.knowledgeBaseId().orElse(id));
        KnowledgeBaseCatalogEntry candidate = entry(candidateKey, command, current);
        return saveAndView(policy.update(current, candidate));
    }

    public Map<String, Object> updateProjectStatus(String projectId,
                                                   String kbId,
                                                   KnowledgeBaseCatalogCommands.StatusChange command) {
        require(command);
        String project = requiredProject(projectId);
        String id = policy.requiredId(kbId);
        KnowledgeBaseCatalogEntry current = repository.find(key(KnowledgeScope.PROJECT, project, id))
                .orElseThrow(() -> new IllegalArgumentException("项目知识库不存在：" + id));
        KnowledgeBaseCatalogEntry candidate = withStatus(current, policy.status(command.status()));
        return saveAndView(policy.update(current, candidate));
    }

    private KnowledgeBaseCatalogEntry entry(KnowledgeBaseCatalogKey key,
                                             KnowledgeBaseCatalogCommands.Mutation command,
                                             KnowledgeBaseCatalogEntry current) {
        String createBy = current != null && !current.createBy().isBlank()
                ? current.createBy()
                : requiredActor(command.actor());
        return new KnowledgeBaseCatalogEntry(
                current == null ? null : current.id(),
                key,
                normalized(command.name().orElse(current == null ? key.kbId() : current.name()), key.kbId()),
                normalized(command.description().orElse(current == null ? "" : current.description()), ""),
                command.status().orElse(current == null ? KnowledgeStatus.ENABLED : current.status()),
                command.documentCount().orElse(current == null ? 0L : current.documentCount()),
                command.chunkCount().orElse(current == null ? 0L : current.chunkCount()),
                normalized(command.sourceType().orElse(current == null ? "DB" : current.sourceType()), "DB"),
                normalized(command.retrievalPolicyJson().orElse(
                        current == null ? "" : current.retrievalPolicyJson()), ""),
                createBy,
                current == null ? "" : current.createdAt(),
                current == null ? "" : current.updatedAt());
    }

    private KnowledgeBaseCatalogEntry materializedEntry(String kbId, String actor) {
        KnowledgeAggregateSnapshot aggregate = aggregate(kbId)
                .orElseThrow(() -> new IllegalArgumentException("通用知识库不存在：" + kbId));
        return new KnowledgeBaseCatalogEntry(
                null,
                key(KnowledgeScope.GLOBAL, "", kbId),
                kbId,
                "由现有 RAG 文档聚合得到的通用知识标签",
                KnowledgeStatus.ENABLED,
                aggregate.documentCount(),
                aggregate.chunkCount(),
                "VECTOR_AGGREGATE",
                "",
                requiredActor(actor),
                "",
                "");
    }

    private KnowledgeBaseCatalogEntry withStatus(KnowledgeBaseCatalogEntry current,
                                                  KnowledgeStatus status) {
        return new KnowledgeBaseCatalogEntry(
                current.id(), current.key(), current.name(), current.description(), status,
                current.documentCount(), current.chunkCount(), current.sourceType(),
                current.retrievalPolicyJson(), current.createBy(), current.createdAt(), current.updatedAt());
    }

    private void save(KnowledgeBaseCatalogEntry entry) {
        repository.save(entry);
    }

    private Map<String, Object> saveAndView(KnowledgeBaseCatalogEntry entry) {
        return view(saveAndLoad(entry), true);
    }

    private KnowledgeBaseCatalogEntry saveAndLoad(KnowledgeBaseCatalogEntry entry) {
        repository.save(entry);
        return repository.find(entry.key()).orElse(entry);
    }

    public Map<String, Object> view(KnowledgeBaseCatalogEntry entry) {
        return view(entry, true);
    }

    private Map<String, Object> view(KnowledgeBaseCatalogEntry entry, boolean includePolicy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entry.id());
        data.put("kbId", entry.key().kbId());
        data.put("knowledgeTag", entry.key().kbId());
        data.put("ragId", entry.key().kbId());
        data.put("name", entry.name());
        data.put("kbName", entry.name());
        data.put("ragName", entry.name());
        data.put("projectId", entry.key().projectId());
        data.put("scope", entry.key().scope().name());
        data.put("description", entry.description());
        data.put("status", entry.status().name());
        data.put("statusCode", entry.status() == KnowledgeStatus.ENABLED ? 1 : 0);
        data.put("documentCount", entry.documentCount());
        data.put("chunkCount", entry.chunkCount());
        data.put("sourceType", entry.sourceType());
        data.put("createBy", entry.createBy());
        data.put("createTime", entry.createdAt());
        data.put("updateTime", entry.updatedAt());
        if (includePolicy) data.put("retrievalPolicyJson", entry.retrievalPolicyJson());
        return data;
    }

    private Map<String, Object> aggregateView(KnowledgeAggregateSnapshot aggregate) {
        String kbId = aggregate.knowledgeBaseId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kbId", kbId);
        data.put("knowledgeTag", kbId);
        data.put("ragId", kbId);
        data.put("name", kbId);
        data.put("kbName", kbId);
        data.put("ragName", kbId);
        data.put("scope", KnowledgeScope.GLOBAL.name());
        data.put("projectId", "");
        data.put("description", "由现有 RAG 文档聚合得到的通用知识标签");
        data.put("status", KnowledgeStatus.ENABLED.name());
        data.put("statusCode", 1);
        data.put("documentCount", aggregate.documentCount());
        data.put("chunkCount", aggregate.chunkCount());
        data.put("ragOrders", aggregate.ragOrders());
        data.put("sourceType", "VECTOR_AGGREGATE");
        data.put("updateTime", "");
        return data;
    }

    private KnowledgeBaseCatalogKey key(KnowledgeScope scope, String projectId, String kbId) {
        return new KnowledgeBaseCatalogKey(scope, projectId, policy.requiredId(kbId));
    }

    private String requiredProject(String projectId) {
        String project = policy.requiredProject(projectId);
        if (!projectExistencePort.exists(project)) {
            throw new IllegalArgumentException("项目不存在：" + project);
        }
        return project;
    }

    private String requiredActor(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("createBy 不能为空，知识库写操作必须绑定真实操作者");
        }
        return normalized;
    }

    private String requiredMutationId(KnowledgeBaseCatalogCommands.Mutation command) {
        String suppliedId = normalized(command.knowledgeBaseId().value(), "");
        String suppliedName = normalized(command.name().value(), "");
        return policy.requiredId(suppliedId.isBlank() ? suppliedName : suppliedId);
    }

    private String normalized(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private List<KnowledgeBaseCatalogEntry> safe(List<KnowledgeBaseCatalogEntry> entries) {
        return entries == null ? List.of() : entries;
    }

    private List<KnowledgeAggregateSnapshot> aggregates() {
        List<KnowledgeAggregateSnapshot> result = aggregatePort.listAggregates();
        return result == null ? List.of() : result;
    }

    private Optional<KnowledgeAggregateSnapshot> aggregate(String kbId) {
        return aggregates().stream()
                .filter(item -> kbId.equals(item.knowledgeBaseId()))
                .findFirst();
    }

    private void require(KnowledgeBaseCatalogCommands.Mutation command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_COMMAND_REQUIRED");
    }

    private void require(KnowledgeBaseCatalogCommands.StatusChange command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_COMMAND_REQUIRED");
    }
}
