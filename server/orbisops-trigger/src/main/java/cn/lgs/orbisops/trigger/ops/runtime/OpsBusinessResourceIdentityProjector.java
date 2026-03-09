package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Projects the project-wide OpenAPI catalog into the business resources that are authoritative
 * for the current run. A successful catalog lookup is discovery authority only; it does not make
 * every operation in that catalog an allowed datasource resource.
 */
final class OpsBusinessResourceIdentityProjector {

    static final String RESOLVED_EVENT = "BUSINESS_RESOURCE_IDENTITY_RESOLVED";
    private static final int MAX_PARSE_DEPTH = 8;

    private OpsBusinessResourceIdentityProjector() {
    }

    static void projectOpenApiResult(
            OpsRuntimeResourceContext context,
            String callbackToolName,
            String toolInput,
            String toolOutput) {
        if (context == null || !isOpenApiListOperations(callbackToolName, toolInput)) return;
        Map<String, Object> envelope = parseMap(toolOutput);
        if (envelope.isEmpty()) return;
        Map<String, Object> catalog = findOpenApiCatalog(envelope, 0);
        if (catalog.isEmpty()) return;

        String query = businessQuery(context);
        if (!StringUtils.hasText(query)) return;
        Set<String> explicitPaths = explicitUserPaths(context);
        String resultId = firstText(
                envelope.get("providerResultId"),
                envelope.get("resultId"));
        String outputHash = firstText(
                envelope.get("providerOutputHash"),
                envelope.get("outputHash"));

        List<Map<String, Object>> identities = new ArrayList<>();
        Object rawOperations = catalog.get("operations");
        if (rawOperations instanceof Iterable<?> operations) {
            for (Object raw : operations) {
                if (!(raw instanceof Map<?, ?> operation)) continue;
                Map<String, Object> normalized = stringMap(operation);
                if (!matchesCurrentBusinessResource(query, explicitPaths, normalized)) continue;
                String endpoint = text(normalized.get("path"));
                if (!StringUtils.hasText(endpoint)) continue;
                Map<String, Object> identity = new LinkedHashMap<>();
                identity.put("businessObject", firstText(
                        normalized.get("summary"),
                        normalized.get("operationId"),
                        endpoint));
                identity.put("endpoint", endpoint);
                identity.put("operationId", text(normalized.get("operationId")));
                identity.put("method", text(normalized.get("method")));
                identity.put("source", "OPENAPI");
                if (StringUtils.hasText(resultId)) identity.put("resultId", resultId);
                if (StringUtils.hasText(outputHash)) identity.put("outputHash", outputHash);
                identities.add(Map.copyOf(identity));
            }
        }
        if (identities.isEmpty()) return;

        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        LinkedHashSet<String> businessObjects = new LinkedHashSet<>();
        identities.forEach(identity -> {
            endpoints.add(text(identity.get("endpoint")));
            businessObjects.add(text(identity.get("businessObject")));
        });
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "OPENAPI");
        payload.put("identities", List.copyOf(identities));
        payload.put("paths", endpoints.stream().filter(StringUtils::hasText).toList());
        payload.put("businessObjects", businessObjects.stream().filter(StringUtils::hasText).toList());
        if (StringUtils.hasText(resultId)) payload.put("resultId", resultId);
        if (StringUtils.hasText(outputHash)) payload.put("outputHash", outputHash);
        context.record(OpsRuntimeEvent.builder()
                .eventType(RESOLVED_EVENT)
                .status("SUCCEEDED")
                .summary("OpenAPI 已将项目级 operation catalog 收敛为当前请求的权威业务资源身份。")
                .payload(Map.copyOf(payload))
                .build());
    }

    static Set<String> resolvedPaths(OpsRuntimeResourceContext context) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (context == null || context.getEvents() == null) return result;
        for (OpsRuntimeEvent event : context.getEvents()) {
            if (event == null
                    || !RESOLVED_EVENT.equalsIgnoreCase(text(event.getEventType()))
                    || !"SUCCEEDED".equalsIgnoreCase(text(event.getStatus()))) {
                continue;
            }
            Map<String, Object> payload = event.getPayload();
            if (payload == null || !"OPENAPI".equalsIgnoreCase(text(payload.get("source")))) continue;
            Object paths = payload.get("paths");
            if (paths instanceof Iterable<?> iterable) {
                for (Object value : iterable) addPath(result, value);
            }
            Object identities = payload.get("identities");
            if (identities instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value instanceof Map<?, ?> identity) addPath(result, identity.get("endpoint"));
                }
            }
        }
        return result;
    }

    static Set<String> explicitUserPaths(OpsRuntimeResourceContext context) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (context == null || context.getRequest() == null) return result;
        OpsAgentChatRequest request = context.getRequest();
        Object original = request.getMetadata() == null
                ? null
                : request.getMetadata().get(OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY);
        if (StringUtils.hasText(text(original))) {
            result.addAll(OpsDatasourceRuntimeToolProvider.apiPaths(text(original)));
            return result;
        }
        result.addAll(OpsDatasourceRuntimeToolProvider.apiPaths(request.getQuery()));
        return result;
    }

    private static boolean isOpenApiListOperations(String callbackToolName, String toolInput) {
        String tool = text(callbackToolName).toLowerCase(Locale.ROOT);
        String input = text(toolInput).toLowerCase(Locale.ROOT);
        return tool.contains("openapi") && input.contains("openapi_list_operations");
    }

    private static String businessQuery(OpsRuntimeResourceContext context) {
        if (context == null || context.getRequest() == null) return "";
        OpsAgentChatRequest request = context.getRequest();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (request.getMetadata() != null) {
            addText(values, request.getMetadata().get(OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY));
            addText(values, request.getMetadata().get(OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY));
        }
        addText(values, request.getQuery());
        return String.join("\n", values);
    }

    private static boolean matchesCurrentBusinessResource(
            String query,
            Set<String> explicitPaths,
            Map<String, Object> operation) {
        String path = text(operation.get("path"));
        if (StringUtils.hasText(path)
                && explicitPaths.contains(path.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String compactQuery = compact(query);
        String summary = compact(text(operation.get("summary")));
        if (summary.length() >= 2 && compactQuery.contains(summary)) return true;
        String operationId = text(operation.get("operationId")).toLowerCase(Locale.ROOT);
        return operationId.length() >= 3 && text(query).toLowerCase(Locale.ROOT).contains(operationId);
    }

    private static Map<String, Object> findOpenApiCatalog(Object value, int depth) {
        if (value == null || depth > MAX_PARSE_DEPTH) return Map.of();
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = stringMap(raw);
            if (map.get("operations") instanceof Iterable<?>) return map;
            for (String key : List.of("providerResult", "rawPreview", "content", "text", "preview", "data", "result")) {
                Map<String, Object> nested = findOpenApiCatalog(map.get(key), depth + 1);
                if (!nested.isEmpty()) return nested;
            }
            return Map.of();
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> nested = findOpenApiCatalog(item, depth + 1);
                if (!nested.isEmpty()) return nested;
            }
            return Map.of();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            String trimmed = string.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return Map.of();
            try {
                return findOpenApiCatalog(JSON.parse(trimmed), depth + 1);
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static Map<String, Object> parseMap(String value) {
        if (!StringUtils.hasText(value)) return Map.of();
        try {
            Object parsed = JSON.parse(value);
            return parsed instanceof Map<?, ?> map ? stringMap(map) : Map.of();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String compact(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    private static void addPath(Set<String> target, Object value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(normalized)) target.add(normalized);
    }

    private static void addText(Set<String> target, Object value) {
        String normalized = text(value);
        if (StringUtils.hasText(normalized)) target.add(normalized);
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (StringUtils.hasText(normalized)) return normalized;
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
