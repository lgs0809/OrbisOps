package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpsAgentCapabilityCatalogService {

    private final ProjectDefinitionApplicationService projectDefinitionService;
    private final ProjectMcpCatalogApplicationService projectMcpCatalogService;
    private final ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService;
    private final ProjectMcpAuthorizationApplicationService projectMcpAuthorizationService;
    private final ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService;
    private final ExecutionResourceQueryApplicationService executionResourceService;

    public OpsAgentCapabilityCatalogService(
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectMcpCatalogApplicationService projectMcpCatalogService,
            ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService,
            ProjectMcpAuthorizationApplicationService projectMcpAuthorizationService,
            ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService,
            ExecutionResourceQueryApplicationService executionResourceService) {
        this.projectDefinitionService = projectDefinitionService;
        this.projectMcpCatalogService = projectMcpCatalogService;
        this.projectKnowledgeAuthorizationService = projectKnowledgeAuthorizationService;
        this.projectMcpAuthorizationService = projectMcpAuthorizationService;
        this.projectSkillAuthorizationService = projectSkillAuthorizationService;
        this.executionResourceService = executionResourceService;
    }

    public Map<String, Object> projectCapabilities(String projectId) {
        String normalizedProjectId = requireExistingProject(projectId);
        Map<String, Object> project = projectDefinitionService.find(normalizedProjectId);
        List<String> projectSkillIds = projectSkillAuthorizationService.localIds(normalizedProjectId);
        List<String> enabledGlobalSkillIds = projectSkillAuthorizationService.globalIds(normalizedProjectId);
        LinkedHashSet<String> skillIdSet = new LinkedHashSet<>(projectSkillIds);
        skillIdSet.addAll(enabledGlobalSkillIds);
        List<String> skillIds = new ArrayList<>(skillIdSet);
        List<String> mcpIds = projectMcpAuthorizationService.enabledIds(normalizedProjectId);
        List<String> knowledgeBaseIds = projectKnowledgeAuthorizationService.enabledIds(normalizedProjectId);
        List<Map<String, Object>> projectTools = capabilityMaps(
                projectMcpCatalogService.list(normalizedProjectId).stream()
                        .map(projectMcpCatalogService::view)
                        .toList(),
                "mcpId", "mcpName", "PROJECT_TOOL");
        Set<String> projectToolIds = projectTools.stream()
                .map(item -> String.valueOf(item.get("mcpId")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> projectKnowledgeBases =
                projectKnowledgeAuthorizationService.projectEntries(normalizedProjectId).stream()
                        .map(entry -> knowledgeCapability(entry, "PROJECT_KNOWLEDGE_BASE"))
                        .toList();
        Set<String> projectKnowledgeBaseIds = projectKnowledgeBases.stream()
                .map(item -> String.valueOf(item.get("kbId")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", normalizedProjectId);
        data.put("projectName", project.getOrDefault("name", normalizedProjectId));
        data.put("skillIds", skillIds);
        data.put("mcpIds", mcpIds);
        data.put("knowledgeBaseIds", knowledgeBaseIds);
        data.put("projectSkills", projectSkillIds.stream()
                .map(skillId -> idCapability("skillId", skillId, "PROJECT_AUTHORIZED"))
                .toList());
        data.put("enabledGlobalSkills", enabledGlobalSkillIds.stream()
                .map(skillId -> idCapability("skillId", skillId, "GLOBAL_ENABLED"))
                .toList());
        data.put("projectTools", projectTools);
        data.put("enabledSharedTools", mcpIds.stream()
                .filter(mcpId -> !projectToolIds.contains(mcpId))
                .map(mcpId -> idCapability("mcpId", mcpId, "SHARED_TOOL"))
                .toList());
        data.put("projectKnowledgeBases", projectKnowledgeBases);
        data.put("enabledGlobalKnowledgeBases",
                projectKnowledgeAuthorizationService.globalEntries(normalizedProjectId).stream()
                        .map(entry -> knowledgeCapability(entry, "GLOBAL_KNOWLEDGE_BASE"))
                        .filter(item -> !projectKnowledgeBaseIds.contains(String.valueOf(item.get("kbId"))))
                        .toList());
        data.put("executionTargets", executionResourceService.list(normalizedProjectId).stream()
                .map(resource -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("executionTargetId", resource.resourceId());
                    item.put("resourceId", resource.resourceId());
                    item.put("targetName", resource.name());
                    item.put("name", resource.name());
                    item.put("projectId", resource.projectId());
                    item.put("adapterType", resource.adapter().code());
                    item.put("adapterTemplateId", resource.adapterTemplateId());
                    item.put("workerId", resource.workerId());
                    item.put("environments", resource.environments());
                    item.put("status", resource.status().name());
                    item.put("capabilityKind", "PROJECT_EXECUTION_TARGET");
                    return item;
                })
                .toList());
        data.put("rawProjectFields", Map.of(
                "knowledgeBaseId", projectKnowledgeAuthorizationService.defaultId(normalizedProjectId),
                "knowledgeBaseIds", knowledgeBaseIds,
                "skillIds", skillIds,
                "mcpIds", mcpIds));
        return data;
    }

    private String requireExistingProject(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("必须提供 projectId");
        }
        String normalizedProjectId = projectId.trim();
        if (!projectDefinitionService.exists(normalizedProjectId)) {
            throw new IllegalArgumentException("项目不存在：" + normalizedProjectId);
        }
        return normalizedProjectId;
    }

    private List<Map<String, Object>> capabilityMaps(Object value,
                                                     String idField,
                                                     String nameField,
                                                     String scope) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> capabilities = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, rawValue) -> map.put(String.valueOf(key), rawValue));
            String id = String.valueOf(map.getOrDefault(idField, ""));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            map.put("id", id);
            map.put("scope", scope);
            map.putIfAbsent(nameField, id);
            capabilities.add(map);
        }
        return capabilities;
    }

    private Map<String, Object> knowledgeCapability(KnowledgeBaseCatalogEntry entry, String scope) {
        Map<String, Object> data = new LinkedHashMap<>();
        String kbId = entry.key().kbId();
        data.put("kbId", kbId);
        data.put("knowledgeTag", kbId);
        data.put("kbName", entry.name());
        data.put("name", entry.name());
        data.put("projectId", entry.key().projectId());
        data.put("description", entry.description());
        data.put("status", entry.status().name());
        data.put("documentCount", entry.documentCount());
        data.put("chunkCount", entry.chunkCount());
        data.put("sourceType", entry.sourceType());
        data.put("id", kbId);
        data.put("scope", scope);
        return data;
    }

    private Map<String, Object> idCapability(String idField, String id, String scope) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(idField, id);
        if ("knowledgeBaseId".equals(idField)) {
            data.put("kbId", id);
            data.put("knowledgeTag", id);
        }
        data.put("id", id);
        data.put("name", id);
        data.put("scope", scope);
        return data;
    }
}
