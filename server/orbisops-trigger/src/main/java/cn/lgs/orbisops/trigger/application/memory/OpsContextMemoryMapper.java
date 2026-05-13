package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger anti-corruption mapper for the historical Context Memory Map contract. */
public class OpsContextMemoryMapper {

    public Map<String, Object> view(ContextMemorySnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", snapshot.id());
        data.put("memoryId", snapshot.memoryId());
        data.put("scopeType", snapshot.scopeType());
        data.put("scopeId", snapshot.scopeId());
        data.put("memoryType", snapshot.memoryType());
        data.put("title", snapshot.title());
        data.put("summary", snapshot.summary());
        data.put("content", snapshot.content());
        data.put("keywords", snapshot.keywords());
        data.put("status", snapshot.status());
        data.put("confidence", snapshot.confidence());
        data.put("sourceType", snapshot.sourceType());
        data.put("sourceId", snapshot.sourceId());
        data.put("sourceMessageHash", snapshot.sourceMessageHash());
        data.put("createdBy", snapshot.createdBy());
        data.put("createTime", snapshot.createTime());
        data.put("updateTime", snapshot.updateTime());
        data.put("expireTime", snapshot.expireTime());
        return data;
    }

    public List<Map<String, Object>> views(List<ContextMemorySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();
        return snapshots.stream()
                .filter(snapshot -> snapshot != null)
                .map(this::view)
                .toList();
    }

    public String keywords(Object value) {
        if (value == null) return "";
        if (value instanceof String text) return text;
        return JSON.toJSONString(value);
    }
}
