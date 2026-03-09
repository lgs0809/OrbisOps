package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IProjectKnowledgeAuthorizationRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeAuthorizationUsageCount;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorizationUsage;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeAuthorizationApplicationService implements KnowledgeAuthorizationQueryPort {

    private final IProjectKnowledgeAuthorizationRepository repository;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeAuthorizationApplicationService(
            IProjectKnowledgeAuthorizationRepository repository) {
        this(repository, new KnowledgeCatalogPolicy());
    }

    KnowledgeAuthorizationApplicationService(
            IProjectKnowledgeAuthorizationRepository repository,
            KnowledgeCatalogPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_REPOSITORY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        this.repository = repository;
        this.policy = policy;
    }

    public ProjectKnowledgeAuthorization enable(KnowledgeGlobalAuthorizationCommand command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_COMMAND_REQUIRED");
        ProjectKnowledgeAuthorization authorization = policy.authorizeGlobal(
                command.projectId(), command.globalKbId(), command.status(), command.enabledBy());
        ProjectKnowledgeAuthorization saved = repository.save(authorization);
        return saved == null ? authorization : saved;
    }

    @Override
    public List<String> enabledKnowledgeBaseIds(String projectId) {
        String project = policy.requiredProject(projectId);
        List<String> ids = repository.listEnabledKnowledgeBaseIds(project);
        return ids == null ? List.of() : ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public List<Map<String, Object>> usageProjects(String globalKbId) {
        String kbId = policy.requiredId(globalKbId);
        List<ProjectKnowledgeAuthorizationUsage> usages = repository.listUsageProjects(kbId);
        return (usages == null ? List.<ProjectKnowledgeAuthorizationUsage>of() : usages).stream()
                .map(this::usageView)
                .toList();
    }

    public Map<String, Long> enabledUsageCounts() {
        List<KnowledgeAuthorizationUsageCount> counts = repository.listEnabledUsageCounts();
        Map<String, Long> result = new LinkedHashMap<>();
        for (KnowledgeAuthorizationUsageCount count : counts == null
                ? List.<KnowledgeAuthorizationUsageCount>of()
                : counts) {
            result.put(count.globalKbId(), count.projectCount());
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> authorizationView(ProjectKnowledgeAuthorization authorization) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", authorization.projectId());
        result.put("globalKbId", authorization.globalKbId());
        result.put("kbId", authorization.globalKbId());
        result.put("status", authorization.status().name());
        result.put("bindingStatus", authorization.status().name());
        result.put("enabledBy", authorization.enabledBy());
        result.put("enabledTime", authorization.enabledAt());
        result.put("updateTime", authorization.updatedAt());
        return Map.copyOf(result);
    }

    private Map<String, Object> usageView(ProjectKnowledgeAuthorizationUsage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", usage.projectId());
        result.put("projectName", usage.projectName());
        result.put("status", usage.status().name());
        result.put("enabledBy", usage.enabledBy());
        result.put("enabledTime", usage.enabledAt());
        result.put("updateTime", usage.updatedAt());
        return Map.copyOf(result);
    }
}
