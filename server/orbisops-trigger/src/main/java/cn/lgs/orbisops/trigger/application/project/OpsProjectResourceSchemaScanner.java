package cn.lgs.orbisops.trigger.application.project;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Selects a protocol probe and applies uniform schema-discovery fallback semantics. */
@Slf4j
@Component
public class OpsProjectResourceSchemaScanner {

    private final List<OpsProjectResourceSchemaProbe> probes;

    public OpsProjectResourceSchemaScanner(List<OpsProjectResourceSchemaProbe> probes) {
        this.probes = probes == null ? List.of() : List.copyOf(probes);
    }

    public Map<String, Object> scan(String resourceType,
                                    String endpoint,
                                    Map<String, Object> credential) {
        String type = normalizeType(resourceType);
        String resolvedEndpoint = text(endpoint, defaultEndpoint(type));
        Map<String, Object> resolvedCredential = credential == null
                ? Map.of()
                : credential;
        try {
            OpsProjectResourceSchemaProbe probe = probes.stream()
                    .filter(candidate -> candidate.supports(type))
                    .findFirst()
                    .orElse(null);
            if (probe == null) {
                return previewSchema("当前资源类型没有可用的真实连接探针；不会生成虚构对象");
            }
            Map<String, Object> scanned = invoke(
                    probe, type, resolvedEndpoint, resolvedCredential);
            int objectCount = objectNames(scanned).size();
            return schemaResult(
                    scanned,
                    "live",
                    objectCount == 0
                            ? "真实连接扫描成功；当前没有可见对象"
                            : "已从真实连接扫描可见对象");
        } catch (ProbeExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("业务系统资源扫描失败，type={}，endpoint={}，error={}",
                    type, resolvedEndpoint, cause.getMessage());
            log.debug("业务系统资源扫描异常详情，type={}，endpoint={}",
                    type, resolvedEndpoint, cause);
            return previewSchema(
                    "真实连接扫描失败；不会生成虚构对象：" + cause.getMessage());
        }
    }

    private Map<String, Object> invoke(OpsProjectResourceSchemaProbe probe,
                                       String resourceType,
                                       String endpoint,
                                       Map<String, Object> credential) {
        try {
            Map<String, Object> result = probe.scan(resourceType, endpoint, credential);
            return result == null ? Map.of("objects", List.of()) : result;
        } catch (Exception e) {
            throw new ProbeExecutionException(e);
        }
    }

    private Map<String, Object> previewSchema(String message) {
        return schemaResult(Map.of("objects", List.of()), "unavailable", message);
    }

    private Map<String, Object> schemaResult(Map<String, Object> schema,
                                             String source,
                                             String message) {
        Map<String, Object> result = new LinkedHashMap<>(schema);
        result.put("source", source);
        result.put("message", message);
        result.putIfAbsent("scannedAt", LocalDateTime.now().toString());
        return result;
    }

    private Set<String> objectNames(Map<String, Object> schema) {
        Set<String> names = new LinkedHashSet<>();
        Object objects = schema.get("objects");
        if (objects instanceof List<?> list) {
            list.forEach(item -> {
                if (item instanceof Map<?, ?> map && map.get("name") != null) {
                    names.add(String.valueOf(map.get("name")));
                }
            });
        }
        return names;
    }

    private String defaultEndpoint(String type) {
        return switch (type) {
            case "mysql" -> "mysql://127.0.0.1:3306/app";
            case "postgresql" -> "postgresql://127.0.0.1:5432/app";
            case "redis" -> "redis://127.0.0.1:6379/0";
            case "rabbitmq" -> "http://127.0.0.1:15672";
            case "elasticsearch" -> "http://127.0.0.1:9200";
            case "prometheus" -> "http://127.0.0.1:9090";
            case "openapi" -> "http://127.0.0.1:8080/v3/api-docs";
            default -> "local";
        };
    }

    private String normalizeType(String type) {
        String value = text(type, "mysql").toLowerCase(Locale.ROOT).replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) return "postgresql";
        if ("elk".equals(value) || "es".equals(value)) return "elasticsearch";
        if ("k8s".equals(value)) return "kubernetes";
        if ("gitlab".equals(value)) return "gitlab_ci";
        if ("http".equals(value) || "api".equals(value)) return "http_api";
        return value;
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private static final class ProbeExecutionException extends RuntimeException {
        private ProbeExecutionException(Throwable cause) {
            super(cause);
        }
    }
}
