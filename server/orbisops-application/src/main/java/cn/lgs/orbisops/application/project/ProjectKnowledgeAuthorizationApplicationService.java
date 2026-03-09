package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.knowledge.KnowledgeAuthorizationQueryPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeWorkspaceCatalogPort;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectKnowledgeAuthorizationApplicationService {

    private final ProjectDefinitionApplicationService definitionService;
    private final KnowledgeAuthorizationQueryPort authorizationService;
    private final KnowledgeWorkspaceCatalogPort catalogPort;

    public ProjectKnowledgeAuthorizationApplicationService(
            ProjectDefinitionApplicationService definitionService,
            KnowledgeAuthorizationQueryPort authorizationService,
            KnowledgeWorkspaceCatalogPort catalogPort) {
        if (definitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        if (authorizationService == null) {
            throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_SERVICE_REQUIRED");
        }
        if (catalogPort == null) {
            throw new IllegalArgumentException("KNOWLEDGE_WORKSPACE_CATALOG_PORT_REQUIRED");
        }
        this.definitionService = definitionService;
        this.authorizationService = authorizationService;
        this.catalogPort = catalogPort;
    }

    public List<String> enabledIds(String projectId) {
        ProjectKnowledgeAuthorization authorization = resolve(projectId);
        LinkedHashSet<String> ids = new LinkedHashSet<>(authorization.localIds());
        ids.addAll(authorization.globalIds());
        return List.copyOf(ids);
    }

    public List<String> localIds(String projectId) {
        return resolve(projectId).localIds();
    }

    public List<String> globalIds(String projectId) {
        return resolve(projectId).globalIds();
    }

    public String defaultId(String projectId) {
        ProjectKnowledgeAuthorization authorization = resolve(projectId);
        if (!authorization.localIds().isEmpty()) {
            return authorization.localIds().get(0);
        }
        if (!authorization.globalIds().isEmpty()) {
            return authorization.globalIds().get(0);
        }
        return "";
    }

    public List<KnowledgeBaseCatalogEntry> projectEntries(String projectId) {
        return resolve(projectId).projectEntries();
    }

    public List<KnowledgeBaseCatalogEntry> globalEntries(String projectId) {
        return resolve(projectId).globalEntries();
    }

    public boolean allows(String projectId, String knowledgeBaseId) {
        String project = text(projectId);
        String kbId = text(knowledgeBaseId);
        return !project.isBlank()
                && !kbId.isBlank()
                && enabledIds(project).contains(kbId);
    }

    public String scope(String projectId, String knowledgeBaseId) {
        String project = text(projectId);
        String kbId = text(knowledgeBaseId);
        if (project.isBlank() || kbId.isBlank()) {
            return "";
        }
        ProjectKnowledgeAuthorization authorization = resolve(project);
        if (authorization.projectCatalogIds().contains(kbId)) {
            return "PROJECT";
        }
        if (authorization.globalIds().contains(kbId)) {
            return "GLOBAL";
        }
        return kbId.equals(authorization.legacyDefaultId())
                ? "LEGACY"
                : "";
    }

    private ProjectKnowledgeAuthorization resolve(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        ProjectDefinition project = definitionService.findDefinition(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
        String legacyDefaultId = text(project.knowledgeBaseId());
        List<KnowledgeBaseCatalogEntry> projectEntries =
                safeEntries(catalogPort.listEnabledProject(id));
        Set<String> projectCatalogIds = projectEntries.stream()
                .map(entry -> entry.key().kbId())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> authorizedGlobalIds =
                authorizationService.enabledKnowledgeBaseIds(id);
        List<KnowledgeBaseCatalogEntry> globalEntries = safeEntries(
                catalogPort.listEnabledGlobalByIds(authorizedGlobalIds));
        List<String> globalIds = globalEntries.stream()
                .map(entry -> entry.key().kbId())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();

        LinkedHashSet<String> localIds = new LinkedHashSet<>();
        if (!legacyDefaultId.isBlank()) {
            localIds.add(legacyDefaultId);
        }
        localIds.addAll(projectCatalogIds);
        return new ProjectKnowledgeAuthorization(
                List.copyOf(localIds),
                globalIds,
                Set.copyOf(projectCatalogIds),
                legacyDefaultId,
                projectEntries,
                globalEntries);
    }

    private List<KnowledgeBaseCatalogEntry> safeEntries(
            List<KnowledgeBaseCatalogEntry> entries) {
        return entries == null ? List.of() : entries;
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ProjectKnowledgeAuthorization(
            List<String> localIds,
            List<String> globalIds,
            Set<String> projectCatalogIds,
            String legacyDefaultId,
            List<KnowledgeBaseCatalogEntry> projectEntries,
            List<KnowledgeBaseCatalogEntry> globalEntries) {
    }
}
