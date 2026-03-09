package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application use case for scene-aware user/project Context Memory selection. */
public class ContextMemorySceneQueryApplicationService {

    private final ContextMemoryStoreApplicationService storeService;
    private final ContextMemoryDefinitionPolicy definitionPolicy;

    public ContextMemorySceneQueryApplicationService(ContextMemoryStoreApplicationService storeService,
                                                     ContextMemoryDefinitionPolicy definitionPolicy) {
        this.storeService = storeService;
        this.definitionPolicy = definitionPolicy == null
                ? new ContextMemoryDefinitionPolicy()
                : definitionPolicy;
    }

    public List<ContextMemorySnapshot> query(ContextMemorySceneQuery query) {
        if (query == null || storeService == null) return List.of();
        List<String> types = definitionPolicy.memoryTypesForScene(query.scene());
        Map<String, ContextMemorySnapshot> selected = new LinkedHashMap<>();
        int perType = Math.max(2, query.limit() / Math.max(1, types.size()) + 1);
        for (String type : types) {
            if (type.startsWith("USER_") && hasText(query.userId())) {
                add(selected, storeService.search(new ContextMemorySearchCriteria(
                        "USER", query.userId(), type, "ACTIVE", perType)));
            } else if (type.startsWith("PROJECT_") && hasText(query.projectId())) {
                add(selected, storeService.search(new ContextMemorySearchCriteria(
                        "PROJECT", query.projectId(), type, "ACTIVE", perType)));
            }
        }
        return selected.values().stream().limit(query.limit()).toList();
    }

    private void add(Map<String, ContextMemorySnapshot> selected,
                     List<ContextMemorySnapshot> memories) {
        if (memories == null || memories.isEmpty()) return;
        memories.stream()
                .filter(memory -> memory != null && hasText(memory.memoryId()))
                .forEach(memory -> selected.put(memory.memoryId(), memory));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
