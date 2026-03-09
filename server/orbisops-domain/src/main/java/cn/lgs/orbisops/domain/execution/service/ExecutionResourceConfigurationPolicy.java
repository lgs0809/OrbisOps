package cn.lgs.orbisops.domain.execution.service;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ExecutionResourceConfigurationPolicy {

    private static final Set<String> MYSQL_ACTIONS = Set.of(
            "MYSQL_CREATE_INDEX", "MYSQL_UPDATE_LIMITED", "MYSQL_DROP_INDEX",
            "MYSQL_SET_GLOBAL_VARIABLE");
    private static final Set<String> REDIS_ACTIONS = Set.of(
            "REDIS_DELETE_KEYS", "REDIS_UPDATE_TTL", "REDIS_CONFIG_SET");
    private static final Set<String> RABBITMQ_DEFINITION_KEYS = Set.of(
            "message-ttl", "expires", "max-length", "max-length-bytes", "overflow",
            "dead-letter-exchange", "dead-letter-routing-key", "queue-mode",
            "consumer-timeout", "delivery-limit");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");
    private static final Pattern SQL_TARGET = Pattern.compile(
            "[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)?");
    private static final Pattern CONFIG_KEY = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    public Map<String, Object> normalize(
            ExecutionAdapterType adapter,
            Map<String, Object> source,
            List<String> environments,
            ExecutionSourceResource sourceResource) {
        if (adapter == null) throw new IllegalArgumentException("EXECUTION_ADAPTER_REQUIRED");
        Map<String, Object> configuration = new LinkedHashMap<>(
                Optional.ofNullable(source).orElse(Map.of()));
        return switch (adapter) {
            case LOCAL_JAVA_SERVICE -> localJava(configuration);
            case MYSQL_CONTROLLED -> mysql(configuration, environments, sourceResource);
            case REDIS_CONTROLLED -> redis(configuration, environments, sourceResource);
            case RABBITMQ_POLICY -> rabbitMq(configuration, environments, sourceResource);
            case DEPLOYMENT_HTTP -> deploymentHttp(configuration, environments);
        };
    }

    private Map<String, Object> localJava(Map<String, Object> configuration) {
        configuration.put("allowedArtifactRoot",
                absolutePath(configuration.get("allowedArtifactRoot"), "allowedArtifactRoot"));
        configuration.put("releaseRoot",
                absolutePath(configuration.get("releaseRoot"), "releaseRoot"));
        Map<String, Object> services = map(configuration.get("services"));
        if (services.isEmpty()) {
            throw new IllegalArgumentException("local-java-service 至少需要配置一个 service");
        }
        if (services.size() > 100) {
            throw new IllegalArgumentException("单个执行资源最多配置 100 个 service");
        }
        Map<String, Object> normalizedServices = new LinkedHashMap<>();
        services.forEach((serviceId, serviceValue) -> {
            id(serviceId, "serviceId");
            Map<String, Object> service = map(serviceValue);
            service.put("healthUrl", safeHttpUrl(service.get("healthUrl"), true));
            List<Map<String, Object>> targets = mapList(service.get("targets"));
            if (targets.isEmpty() || targets.size() > 20) {
                throw new IllegalArgumentException(
                        "service " + serviceId + " 的 targets 数量必须为 1-20");
            }
            service.put("healthTimeoutMs",
                    positiveInt(service.get("healthTimeoutMs"), 90000, 1000, 600000));
            service.put("targets", targets.stream().map(this::localJavaTarget).toList());
            normalizedServices.put(serviceId, service);
        });
        configuration.put("services", normalizedServices);
        return configuration;
    }

    private Map<String, Object> localJavaTarget(Map<String, Object> target) {
        Map<String, Object> normalized = new LinkedHashMap<>(target);
        normalized.put("artifactTarget", absolutePath(target.get("artifactTarget"), "artifactTarget"));
        normalized.put("pidFile", absolutePath(target.get("pidFile"), "pidFile"));
        normalized.put("logFile", absolutePath(target.get("logFile"), "logFile"));
        String javaBinary = text(target.get("javaBinary"), "java");
        if (!"java".equals(javaBinary)) {
            throw new IllegalArgumentException("local-java-service 仅允许使用 java 启动器");
        }
        normalized.put("javaBinary", "java");
        List<String> fixedArgs = stringList(target.get("fixedArgs"));
        if (fixedArgs.size() > 100 || fixedArgs.stream()
                .anyMatch(item -> item.length() > 1000 || item.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("fixedArgs 超出安全限制");
        }
        normalized.put("fixedArgs", fixedArgs);
        Map<String, Object> environment = map(target.get("environment"));
        if (environment.size() > 50 || environment.entrySet().stream()
                .anyMatch(entry -> !ID.matcher(entry.getKey()).matches()
                        || text(entry.getValue(), "").length() > 4000)) {
            throw new IllegalArgumentException("运行环境变量超出安全限制");
        }
        normalized.put("environment", environment);
        return normalized;
    }

    private Map<String, Object> mysql(
            Map<String, Object> configuration,
            List<String> environments,
            ExecutionSourceResource sourceResource) {
        bindSourceResource(configuration, environments, sourceResource, "mysql");
        rejectPlainPassword(configuration, "mysql-controlled");
        configuration.put("endpoint", safeMysqlEndpoint(configuration.get("endpoint")));
        configuration.put("username", id(text(configuration.get("username"), ""), "username"));
        configuration.put("passwordFile",
                absolutePath(configuration.get("passwordFile"), "passwordFile"));
        String idempotencyTable = text(configuration.get("idempotencyTable"), "");
        if (!SQL_TARGET.matcher(idempotencyTable).matches()) {
            throw new IllegalArgumentException("mysql-controlled idempotencyTable 必须是安全表名");
        }
        configuration.put("idempotencyTable", idempotencyTable);
        List<String> allowedObjects = normalizedStrings(configuration.get("allowedObjects"), false);
        if (allowedObjects.isEmpty() || allowedObjects.size() > 200
                || allowedObjects.stream().anyMatch(item -> !SQL_TARGET.matcher(item).matches())) {
            throw new IllegalArgumentException("mysql-controlled allowedObjects 必须包含 1-200 个安全表名");
        }
        List<String> allowedActions = upperStrings(configuration.get("allowedActions"));
        if (allowedActions.isEmpty() || !MYSQL_ACTIONS.containsAll(allowedActions)) {
            throw new IllegalArgumentException("mysql-controlled allowedActions 超出受控动作范围");
        }
        List<String> allowedVariables = normalizedStrings(
                configuration.get("allowedVariables"), true);
        if (allowedActions.contains("MYSQL_SET_GLOBAL_VARIABLE")
                && (allowedVariables.isEmpty() || allowedVariables.size() > 100
                || allowedVariables.stream().anyMatch(item -> !CONFIG_KEY.matcher(item).matches()))) {
            throw new IllegalArgumentException(
                    "启用 MYSQL_SET_GLOBAL_VARIABLE 时必须配置 1-100 个 allowedVariables");
        }
        configuration.put("allowedObjects", allowedObjects);
        configuration.put("allowedActions", allowedActions);
        configuration.put("allowedVariables", allowedVariables);
        configuration.put("connectTimeoutMs",
                positiveInt(configuration.get("connectTimeoutMs"), 5000, 1000, 30000));
        configuration.put("queryTimeoutMs",
                positiveInt(configuration.get("queryTimeoutMs"), 15000, 1000, 120000));
        configuration.put("maxAffectedRows",
                positiveInt(configuration.get("maxAffectedRows"), 1000, 1, 10000));
        configuration.put("ssl", Boolean.parseBoolean(text(configuration.get("ssl"), "false")));
        return configuration;
    }

    private Map<String, Object> redis(
            Map<String, Object> configuration,
            List<String> environments,
            ExecutionSourceResource sourceResource) {
        bindSourceResource(configuration, environments, sourceResource, "redis");
        rejectPlainPassword(configuration, "redis-controlled");
        configuration.put("endpoint", safeRedisEndpoint(configuration.get("endpoint")));
        String username = text(configuration.get("username"), "");
        if (hasText(username)) configuration.put("username", id(username, "username"));
        else configuration.remove("username");
        String passwordFile = text(configuration.get("passwordFile"), "");
        if (hasText(passwordFile)) configuration.put("passwordFile", absolutePath(passwordFile, "passwordFile"));
        else configuration.remove("passwordFile");
        List<String> allowedPatterns = normalizedStrings(configuration.get("allowedPatterns"), false);
        if (allowedPatterns.isEmpty() || allowedPatterns.size() > 200
                || allowedPatterns.stream().anyMatch(item -> "*".equals(item) || item.startsWith("*"))) {
            throw new IllegalArgumentException(
                    "redis-controlled allowedPatterns 必须包含有前缀的 Key Pattern");
        }
        List<String> allowedActions = upperStrings(configuration.get("allowedActions"));
        if (allowedActions.isEmpty() || !REDIS_ACTIONS.containsAll(allowedActions)) {
            throw new IllegalArgumentException("redis-controlled allowedActions 超出受控动作范围");
        }
        String prefix = text(configuration.get("idempotencyPrefix"), "__ops_change__:");
        if (!prefix.startsWith("__ops_change__:") || prefix.contains("*") || prefix.contains("?")) {
            throw new IllegalArgumentException(
                    "redis-controlled idempotencyPrefix 必须使用 __ops_change__: 前缀");
        }
        if (allowedPatterns.stream().anyMatch(item -> item.startsWith("__ops_change__:"))) {
            throw new IllegalArgumentException("业务 Key Pattern 不能覆盖 Worker 幂等与快照前缀");
        }
        List<String> allowedConfigKeys = normalizedStrings(
                configuration.get("allowedConfigKeys"), true);
        if (allowedActions.contains("REDIS_CONFIG_SET")
                && (allowedConfigKeys.isEmpty() || allowedConfigKeys.size() > 100
                || allowedConfigKeys.stream().anyMatch(item -> !CONFIG_KEY.matcher(item).matches()))) {
            throw new IllegalArgumentException(
                    "启用 REDIS_CONFIG_SET 时必须配置 1-100 个 allowedConfigKeys");
        }
        configuration.put("allowedPatterns", allowedPatterns);
        configuration.put("allowedActions", allowedActions);
        configuration.put("allowedConfigKeys", allowedConfigKeys);
        configuration.put("idempotencyPrefix", prefix);
        configuration.put("connectTimeoutMs",
                positiveInt(configuration.get("connectTimeoutMs"), 5000, 1000, 30000));
        configuration.put("commandTimeoutMs",
                positiveInt(configuration.get("commandTimeoutMs"), 15000, 1000, 120000));
        configuration.put("maxKeys",
                positiveInt(configuration.get("maxKeys"), 100, 1, 10000));
        configuration.put("maxSnapshotBytes",
                positiveInt(configuration.get("maxSnapshotBytes"), 10485760, 1024, 104857600));
        configuration.put("snapshotRetentionSeconds",
                positiveInt(configuration.get("snapshotRetentionSeconds"), 86400, 3600, 604800));
        return configuration;
    }

    private Map<String, Object> rabbitMq(
            Map<String, Object> configuration,
            List<String> environments,
            ExecutionSourceResource sourceResource) {
        bindSourceResource(configuration, environments, sourceResource, "rabbitmq");
        rejectPlainPassword(configuration, "rabbitmq-policy");
        configuration.put("baseUrl", safeHttpUrl(configuration.get("baseUrl"), false));
        configuration.put("username", id(text(configuration.get("username"), ""), "username"));
        configuration.put("passwordFile",
                absolutePath(configuration.get("passwordFile"), "passwordFile"));
        List<String> allowedVhosts = normalizedStrings(configuration.get("allowedVhosts"), false);
        if (allowedVhosts.isEmpty() || allowedVhosts.size() > 100
                || allowedVhosts.stream().anyMatch(item -> item.length() > 200 || item.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("rabbitmq-policy allowedVhosts 格式无效");
        }
        List<String> policyPrefixes = normalizedStrings(
                configuration.get("allowedPolicyPrefixes"), false);
        if (policyPrefixes.isEmpty() || policyPrefixes.size() > 100
                || policyPrefixes.stream().anyMatch(item -> !item.matches("[A-Za-z0-9._-]{1,100}"))) {
            throw new IllegalArgumentException("rabbitmq-policy allowedPolicyPrefixes 格式无效");
        }
        List<String> definitionKeys = normalizedStrings(
                configuration.get("allowedDefinitionKeys"), false);
        if (definitionKeys.isEmpty() || !RABBITMQ_DEFINITION_KEYS.containsAll(definitionKeys)) {
            throw new IllegalArgumentException(
                    "rabbitmq-policy allowedDefinitionKeys 超出安全范围");
        }
        configuration.put("allowedVhosts", allowedVhosts);
        configuration.put("allowedPolicyPrefixes", policyPrefixes);
        configuration.put("allowedDefinitionKeys", definitionKeys);
        configuration.put("maxAffectedObjects",
                positiveInt(configuration.get("maxAffectedObjects"), 100, 1, 10000));
        configuration.put("requestTimeoutMs",
                positiveInt(configuration.get("requestTimeoutMs"), 15000, 1000, 120000));
        return configuration;
    }

    private Map<String, Object> deploymentHttp(
            Map<String, Object> configuration,
            List<String> environments) {
        configuration.put("baseUrl", safeHttpUrl(configuration.get("baseUrl"), false));
        String tokenFile = text(configuration.get("tokenFile"), "");
        if (hasText(tokenFile)) configuration.put("tokenFile", absolutePath(tokenFile, "tokenFile"));
        if (environments == null || environments.isEmpty()) {
            throw new IllegalArgumentException("deployment-http 至少需要一个 environment");
        }
        return configuration;
    }

    private void bindSourceResource(
            Map<String, Object> configuration,
            List<String> environments,
            ExecutionSourceResource sourceResource,
            String expectedType) {
        String sourceResourceId = id(text(configuration.get("sourceResourceId"), ""),
                "sourceResourceId");
        if (sourceResource == null || !sourceResourceId.equals(sourceResource.resourceId())) {
            throw new IllegalArgumentException("项目只读资源不存在：" + sourceResourceId);
        }
        if (!expectedType.equalsIgnoreCase(sourceResource.type())) {
            throw new IllegalArgumentException(
                    "执行资源类型与 sourceResourceId 不一致：" + sourceResource.type());
        }
        if (environments == null || environments.stream()
                .noneMatch(sourceResource.environment()::equalsIgnoreCase)) {
            throw new IllegalArgumentException(
                    "执行资源环境必须包含 sourceResourceId 的环境：" + sourceResource.environment());
        }
        configuration.put("sourceResourceId", sourceResourceId);
    }

    private void rejectPlainPassword(Map<String, Object> configuration, String adapter) {
        if (configuration.containsKey("password")) {
            throw new IllegalArgumentException(adapter + " 禁止保存明文 password，请使用 passwordFile");
        }
    }

    private String absolutePath(Object value, String field) {
        String raw = text(value, "");
        if (!hasText(raw)) throw new IllegalArgumentException(field + " 不能为空");
        Path path = Path.of(raw).normalize();
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(field + " 必须是 Worker 主机上的绝对路径");
        }
        return path.toString();
    }

    private String safeHttpUrl(Object value, boolean allowEmpty) {
        String raw = text(value, "");
        if (!hasText(raw) && allowEmpty) return "";
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("URL 格式无效");
        }
        if (!Set.of("http", "https").contains(uri.getScheme()) || !hasText(uri.getHost())) {
            throw new IllegalArgumentException("URL 必须使用 http/https");
        }
        return raw;
    }

    private String safeMysqlEndpoint(Object value) {
        String raw = text(value, "");
        String uriValue = raw.startsWith("jdbc:") ? raw.substring("jdbc:".length()) : raw;
        URI uri;
        try {
            uri = URI.create(uriValue);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("MySQL endpoint 格式无效");
        }
        if (!"mysql".equalsIgnoreCase(uri.getScheme()) || !hasText(uri.getHost())
                || !hasText(uri.getPath()) || "/".equals(uri.getPath())
                || hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException(
                    "MySQL endpoint 必须包含 mysql://host:port/database");
        }
        return raw;
    }

    private String safeRedisEndpoint(Object value) {
        String raw = text(value, "");
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Redis endpoint 格式无效");
        }
        if (!Set.of("redis", "rediss").contains(uri.getScheme())
                || !hasText(uri.getHost()) || hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException(
                    "Redis endpoint 必须使用 redis:// 或 rediss://");
        }
        return raw;
    }

    private List<String> normalizedStrings(Object value, boolean lowercase) {
        return stringList(value).stream()
                .map(String::trim)
                .filter(this::hasText)
                .map(item -> lowercase ? item.toLowerCase(Locale.ROOT) : item)
                .distinct()
                .toList();
    }

    private List<String> upperStrings(Object value) {
        return stringList(value).stream()
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private int positiveInt(Object value, int fallback, int min, int max) {
        int result;
        try {
            result = Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (RuntimeException ignored) {
            result = fallback;
        }
        if (result < min || result > max) {
            throw new IllegalArgumentException("数值必须位于 " + min + "-" + max);
        }
        return result;
    }

    private String id(String value, String field) {
        String normalized = text(value, "");
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " 格式无效");
        }
        return normalized;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(this::map).toList();
    }

    private String text(Object value, String fallback) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return hasText(result) ? result : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
