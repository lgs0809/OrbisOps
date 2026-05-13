package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemorySelectionReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger mapper from typed authoritative memory references to the compatibility Map contract. */
public class OpsMemorySelectionReferenceMapper {

    public Map<String, Object> view(MemorySelectionReference reference) {
        if (reference == null) return Map.of();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("memoryId", reference.memoryId());
        view.put("version", reference.version());
        view.put("memoryHash", reference.memoryHash());
        view.put("memoryType", reference.memoryType());
        view.put("scope", reference.scope());
        view.put("scopeId", reference.scopeId());
        view.put("sourceMessageHash", reference.sourceMessageHash());
        view.put("contentHash", reference.contentHash());
        view.put("selectedAt", reference.selectedAt());
        view.put("verified", reference.verified());
        return view;
    }

    public List<Map<String, Object>> views(List<MemorySelectionReference> references) {
        if (references == null || references.isEmpty()) return List.of();
        return references.stream().filter(reference -> reference != null).map(this::view).toList();
    }
}
