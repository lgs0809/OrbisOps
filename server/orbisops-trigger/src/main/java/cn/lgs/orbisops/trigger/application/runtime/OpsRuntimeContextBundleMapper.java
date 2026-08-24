package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleCreateCommand;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleLayerInput;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpsRuntimeContextBundleMapper {

    public RuntimeContextBundleCreateCommand createCommand(
            OpsAgentChatRequest request,
            String memoryContext,
            Map<String, Object> metadata,
            int selectedSkillLimit) {
        Map<String, Object> safeMetadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        if (request != null && request.getAgentVersion() != null && request.getAgentVersion() > 0) {
            safeMetadata.put("agentVersion", request.getAgentVersion());
        }
        List<Map<String, Object>> memoryRefs = authoritativeMemoryRefs(safeMetadata);
        return new RuntimeContextBundleCreateCommand(
                layerInput(request, memoryContext, safeMetadata),
                memoryRefs,
                (request == null || request.getTrustedSkillBindings() == null
                        ? new cn.lgs.orbisops.trigger.ops.runtime.OpsExplicitSkillBindings(List.of())
                        : request.getTrustedSkillBindings()).merge(requestedSkillIds(safeMetadata)),
                selectedSkillLimit);
    }

    public RuntimeContextBundleLayerInput layerInput(
            OpsAgentChatRequest request,
            String memoryContext,
            Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        if (request != null && request.getAgentVersion() != null && request.getAgentVersion() > 0) {
            safeMetadata.put("agentVersion", request.getAgentVersion());
        }
        String projectId = firstText(safeMetadata.get("projectId"), request == null ? null : request.getProjectId());
        String agentId = firstText(safeMetadata.get("agentId"), request == null ? null : request.getAgentDefinitionId());
        return new RuntimeContextBundleLayerInput(
                "ctx-" + UUID.randomUUID(),
                text(request == null ? null : request.getRunId()),
                text(request == null ? null : request.getSessionId()),
                projectId,
                agentId,
                text(request == null ? null : request.getUserId()),
                text(request == null ? null : request.getQuery()),
                text(memoryContext),
                safeMetadata,
                Instant.now());
    }

    public Map<String, Object> view(RuntimeContextBundleSnapshot snapshot) {
        return snapshot == null ? Map.of() : snapshot.compatiblePayload();
    }

    private List<Map<String, Object>> authoritativeMemoryRefs(Map<String, Object> metadata) {
        Object value = metadata.get("_authoritativeMemorySelection");
        if (value instanceof OpsMemorySelection selection) return selection.refs();
        return List.of();
    }

    private List<String> requestedSkillIds(Map<String, Object> metadata) {
        Object nested = metadata.get("skillContext");
        Map<String, Object> skillContext = nested instanceof Map<?, ?> map ? stringMap(map) : Map.of();
        Object value = firstNonNull(
                metadata.get("requestedSkillIds"),
                metadata.get("selectedSkillIds"),
                skillContext.get("requestedSkillIds"),
                skillContext.get("selectedSkillIds"),
                metadata.get("skillRefs"),
                metadata.get("selectedSkills"),
                skillContext.get("skillRefs"),
                skillContext.get("selected"));
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                String skillId = text(map.get("skillId"));
                if (!skillId.isBlank()) result.add(skillId);
            } else {
                String skillId = text(item);
                if (!skillId.isBlank()) result.add(skillId);
            }
        }
        return result.stream().distinct().toList();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
