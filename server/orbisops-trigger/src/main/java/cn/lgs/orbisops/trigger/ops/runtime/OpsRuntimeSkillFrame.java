package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.*;

/** Immutable per-run Skill references, captured only after persisted bundle creation or verified reuse. */
public record OpsRuntimeSkillFrame(String projectId, String userId, String runId, String agentId,
                                  String query, List<Map<String,Object>> catalogRefs,
                                  List<Map<String,Object>> selectedRefs) {
    public OpsRuntimeSkillFrame {
        projectId = text(projectId); userId = text(userId); runId = text(runId);
        agentId = text(agentId); query = text(query);
        catalogRefs = copy(catalogRefs); selectedRefs = copy(selectedRefs);
    }
    static OpsRuntimeSkillFrame capture(OpsAgentChatRequest request) {
        return new OpsRuntimeSkillFrame(request.getProjectId(), request.getUserId(), request.getRunId(),
                request.getAgentDefinitionId(), request.getQuery(), refs(request, "skillCatalogRefs"),
                refs(request, "usedSkillVersionRefs"));
    }
    public OpsRuntimeResourceContext context() {
        Map<String,Object> metadata = new LinkedHashMap<>();
        metadata.put("skillCatalogRefs", catalogRefs); metadata.put("usedSkillVersionRefs", selectedRefs);
        return OpsRuntimeResourceContext.builder().projectId(projectId)
                .definition(OpsAgentDefinition.builder().agentId(agentId).projectId(projectId).build())
                .request(OpsAgentChatRequest.builder().projectId(projectId).userId(userId).runId(runId)
                        .agentDefinitionId(agentId).query(query).metadata(metadata).build()).build();
    }
    private static List<Map<String,Object>> refs(OpsAgentChatRequest request, String key) {
        Object raw = request.getMetadata().get(key);
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("SKILL_RUNTIME_REF_INVALID");
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("SKILL_RUNTIME_REF_INVALID");
            Map<String,Object> item = new LinkedHashMap<>();
            map.forEach((k,v) -> item.put(String.valueOf(k), v)); result.add(item);
        }
        return result;
    }
    private static List<Map<String,Object>> copy(List<Map<String,Object>> refs) {
        if (refs == null) return List.of();
        return refs.stream().map(ref -> {
            Map<String,Object> copy = new LinkedHashMap<>();
            ref.forEach((k,v) -> copy.put(k, immutable(v)));
            return Collections.unmodifiableMap(copy);
        }).toList();
    }
    private static Object immutable(Object value) {
        if (value instanceof List<?> list) return list.stream().map(OpsRuntimeSkillFrame::immutable).toList();
        if (value instanceof Map<?,?> map) {
            Map<String,Object> copy = new LinkedHashMap<>();
            map.forEach((k,v) -> copy.put(String.valueOf(k), immutable(v)));
            return Collections.unmodifiableMap(copy);
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        throw new IllegalArgumentException("SKILL_RUNTIME_REF_VALUE_INVALID");
    }
    private static String text(String value) { return value == null ? "" : value; }
}
