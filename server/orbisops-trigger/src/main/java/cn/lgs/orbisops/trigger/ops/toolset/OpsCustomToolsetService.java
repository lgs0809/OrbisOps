package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.CustomToolDefinitionRecord;
import cn.lgs.orbisops.application.toolset.CustomToolsetRecord;
import cn.lgs.orbisops.application.toolset.CustomToolsetStoreApplicationService;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Legacy Trigger DTO facade over the custom Toolset application store. */
@Service
public class OpsCustomToolsetService {

    private final CustomToolsetStoreApplicationService storeService;

    public OpsCustomToolsetService(
            CustomToolsetStoreApplicationService storeService) {
        this.storeService = storeService;
    }

    public List<OpsToolsetDefinition> listCustomToolsets(String projectId) {
        return storeService.list(text(projectId)).stream()
                .map(this::definitionView)
                .toList();
    }

    public OpsToolsetDefinition registerCustomToolset(
            String projectId,
            Map<String, Object> request,
            String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String id = text(safe.get("toolsetId"));
        if (id.isBlank()) id = "custom-" + UUID.randomUUID();
        String adapterType = fallback(
                safe.get("adapterType"),
                "MCP").toUpperCase(Locale.ROOT);
        CustomToolsetRecord record = new CustomToolsetRecord(
                text(projectId),
                id,
                fallback(safe.get("name"), id),
                text(safe.get("description")),
                fallback(safe.get("prerequisites"), text(projectId)),
                strings(safe.get("tags")),
                "CUSTOM",
                adapterType,
                bool(safe.get("enabled"), true),
                bool(safe.get("readOnlyDefault"), true),
                tools(safe.get("tools"), adapterType),
                text(actor),
                text(actor),
                "",
                "");
        return definitionView(storeService.upsert(record));
    }

    public void setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        storeService.setEnabled(
                text(projectId),
                text(toolsetId),
                enabled,
                text(actor));
    }

    private OpsToolsetDefinition definitionView(CustomToolsetRecord record) {
        return OpsToolsetDefinition.builder()
                .toolsetId(record.toolsetId())
                .name(record.name())
                .description(record.description())
                .prerequisites(record.prerequisites())
                .tags(record.tags())
                .sourceType(record.sourceType())
                .adapterType(record.adapterType())
                .enabled(record.enabled())
                .readOnlyDefault(record.readOnlyDefault())
                .tools(record.tools().stream()
                        .map(this::toolView)
                        .toList())
                .createBy(record.createBy())
                .updateBy(record.updateBy())
                .createTime(record.createTime())
                .updateTime(record.updateTime())
                .build();
    }

    private OpsToolDefinition toolView(CustomToolDefinitionRecord tool) {
        return OpsToolDefinition.builder()
                .toolName(tool.toolName())
                .displayName(tool.displayName())
                .description(tool.description())
                .parametersJson(tool.parametersJson())
                .adapterType(tool.adapterType())
                .commandTemplate(tool.commandTemplate())
                .mcpServerId(tool.mcpServerId())
                .remoteToolName(tool.remoteToolName())
                .httpConfigJson(tool.httpConfigJson())
                .dbConfigJson(tool.dbConfigJson())
                .readOnly(tool.readOnly())
                .writesRepairWorkspace(tool.writesRepairWorkspace())
                .writesTargetResource(tool.writesTargetResource())
                .requiresChangePackage(tool.requiresChangePackage())
                .requiresApproval(tool.requiresApproval())
                .riskLevel(tool.riskLevel())
                .outputBudgetJson(tool.outputBudgetJson())
                .enabled(tool.enabled())
                .build();
    }

    private List<CustomToolDefinitionRecord> tools(
            Object value,
            String defaultAdapterType) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<CustomToolDefinitionRecord> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> source)) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((key, entry) ->
                    map.put(String.valueOf(key), entry));
            result.add(new CustomToolDefinitionRecord(
                    fallback(first(map.get("toolName"), map.get("name")), ""),
                    fallback(first(map.get("displayName"), map.get("name")), ""),
                    text(map.get("description")),
                    json(first(map.get("parametersJson"), map.get("parameters"))),
                    fallback(map.get("adapterType"), defaultAdapterType)
                            .toUpperCase(Locale.ROOT),
                    text(map.get("commandTemplate")),
                    text(map.get("mcpServerId")),
                    text(map.get("remoteToolName")),
                    json(first(map.get("httpConfigJson"), map.get("httpConfig"))),
                    json(first(map.get("dbConfigJson"), map.get("dbConfig"))),
                    bool(map.get("readOnly"), true),
                    bool(map.get("writesRepairWorkspace"), false),
                    bool(map.get("writesTargetResource"), false),
                    bool(map.get("requiresChangePackage"), false),
                    bool(map.get("requiresApproval"), false),
                    fallback(map.get("riskLevel"), "LOW")
                            .toUpperCase(Locale.ROOT),
                    json(first(
                            map.get("outputBudgetJson"),
                            map.get("outputBudget"))),
                    bool(map.get("enabled"), true)));
        }
        return List.copyOf(result);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> {
            String text = text(item);
            if (!text.isBlank()) result.add(text);
        });
        return List.copyOf(result);
    }

    private String json(Object value) {
        String text = text(value);
        if (!text.isBlank() && (text.startsWith("{") || text.startsWith("["))) {
            return text;
        }
        return JSON.toJSONString(value == null ? Map.of() : value);
    }

    private Object first(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null && !text(value).isBlank()) return value;
        }
        return null;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = text(value);
        return text.isBlank()
                ? fallback
                : Boolean.parseBoolean(text) || "1".equals(text);
    }

    private String fallback(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
