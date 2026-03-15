package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectResourceCredentialResolutionPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Controls persisted project-resource credential references and runtime resolution. */
@Component
public class OpsProjectResourceCredentialPolicy
        implements ProjectResourceCredentialResolutionPort {

    private final OpsSecretResolver secretResolver;

    public OpsProjectResourceCredentialPolicy(OpsSecretResolver secretResolver) {
        if (secretResolver == null) {
            throw new IllegalArgumentException("OPS_SECRET_RESOLVER_REQUIRED");
        }
        this.secretResolver = secretResolver;
    }

    public Map<String, Object> prepare(Map<String, Object> command,
                                       String resourceType,
                                       boolean partialUpdate,
                                       Map<String, Object> existingCredential) {
        Map<String, Object> request = command == null ? Map.of() : command;
        Map<String, Object> existing = normalizeStored(existingCredential);
        String type = normalizeType(resourceType);
        String username = text(request.get("username"),
                partialUpdate ? text(existing.get("username"), "") : defaultUsername(type));
        String suppliedPassword = text(request.get("password"), "");
        String passwordRef = text(request.get("passwordRef"),
                partialUpdate ? text(existing.get("passwordRef"), "") : "");
        if (StringUtils.hasText(suppliedPassword)) {
            requireReference(suppliedPassword,
                    "资源凭据不能保存明文，请填写 ${env:VAR_NAME} Secret 引用");
            passwordRef = suppliedPassword;
        }
        if (StringUtils.hasText(passwordRef)) {
            requireReference(passwordRef, "passwordRef 必须使用 ${env:VAR_NAME} 格式");
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("username", username);
        credential.put("passwordRef", passwordRef);
        credential.put("configured", StringUtils.hasText(username) || StringUtils.hasText(passwordRef));
        credential.put("updatedAt", LocalDateTime.now().toString());
        return credential;
    }

    public Map<String, Object> normalizeStored(Map<String, Object> storedCredential) {
        Map<String, Object> stored = storedCredential == null ? Map.of() : storedCredential;
        Map<String, Object> normalized = new LinkedHashMap<>();
        String username = text(stored.get("username"), "");
        String passwordRef = text(stored.get("passwordRef"), "");
        String legacyPassword = text(stored.get("password"), "");
        if (!StringUtils.hasText(passwordRef)
                && StringUtils.hasText(legacyPassword)
                && secretResolver.isReference(legacyPassword)) {
            passwordRef = legacyPassword;
        }
        normalized.put("username", username);
        normalized.put("passwordRef", passwordRef);
        normalized.put("configured", StringUtils.hasText(username) || StringUtils.hasText(passwordRef));
        normalized.put("updatedAt", stored.get("updatedAt"));
        return normalized;
    }

    @Override
    public Map<String, Object> resolve(Map<String, Object> storedCredential) {
        Map<String, Object> normalized = normalizeStored(storedCredential);
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("username", text(normalized.get("username"), ""));
        resolved.put("password", secretResolver.resolve(text(normalized.get("passwordRef"), "")));
        return resolved;
    }

    private void requireReference(String value, String message) {
        if (!secretResolver.isReference(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String defaultUsername(String type) {
        return switch (normalizeType(type)) {
            case "mysql" -> "root";
            case "postgresql" -> "postgres";
            case "rabbitmq" -> "guest";
            default -> "";
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
}
