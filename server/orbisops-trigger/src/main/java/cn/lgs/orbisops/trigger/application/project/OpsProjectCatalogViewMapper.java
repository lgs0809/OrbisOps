package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectCatalogEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger compatibility projection for typed project catalog entries. */
public final class OpsProjectCatalogViewMapper {

    private OpsProjectCatalogViewMapper() {
    }

    public static List<Map<String, Object>> views(List<ProjectCatalogEntry> entries) {
        return entries == null ? List.of() : entries.stream()
                .map(OpsProjectCatalogViewMapper::view)
                .toList();
    }

    public static Map<String, Object> view(ProjectCatalogEntry entry) {
        if (entry == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", entry.projectId());
        result.put("name", entry.name());
        result.put("description", entry.description());
        result.put("owner", entry.owner());
        result.put("environments", entry.environments());
        result.put("knowledgeBaseId", entry.knowledgeBaseId());
        result.put("defaultAgentId", entry.defaultAgentId());
        result.put("skillIds", entry.skillIds());
        result.put("sharedMcpIds", entry.sharedMcpIds());
        result.put("enabled", entry.enabled());
        result.put("createdAt", entry.createdAt() == null ? "" : entry.createdAt().toString());
        result.put("updatedAt", entry.updatedAt() == null ? "" : entry.updatedAt().toString());
        return result;
    }
}
