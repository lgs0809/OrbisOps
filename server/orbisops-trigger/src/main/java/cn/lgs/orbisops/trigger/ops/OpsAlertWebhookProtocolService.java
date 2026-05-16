package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Alertmanager payload, matching, signature, and dedup protocol boundary. */
@Slf4j
final class OpsAlertWebhookProtocolService {

    private final LongSupplier currentEpochSeconds;

    OpsAlertWebhookProtocolService() {
        this(() -> java.time.Instant.now().getEpochSecond());
    }

    OpsAlertWebhookProtocolService(LongSupplier currentEpochSeconds) {
        this.currentEpochSeconds = currentEpochSeconds;
    }

    List<Map<String, Object>> extractAlerts(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        Object alerts = payload.get("alerts");
        if (alerts instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> toStringObjectMap((Map<?, ?>) item))
                    .toList();
        }
        return List.of(payload);
    }

    AlertView alertView(Map<String, Object> alert) {
        Map<String, Object> labels = toStringObjectMap(mapValue(alert.get("labels")));
        Map<String, Object> annotations = toStringObjectMap(mapValue(alert.get("annotations")));
        String alertName = firstText(
                value(labels.get("alertname")),
                value(labels.get("alertName")),
                value(alert.get("alertname")));
        String severity = firstText(
                value(labels.get("severity")),
                value(labels.get("level")),
                "warning");
        String service = firstText(
                value(labels.get("service")),
                value(labels.get("app")),
                value(labels.get("job")),
                value(labels.get("namespace")));
        String fingerprint = firstText(
                value(alert.get("fingerprint")),
                stableFallbackFingerprint(labels));
        String status = firstText(value(alert.get("status")), "firing").toLowerCase();
        return new AlertView(
                alertName,
                severity,
                service,
                fingerprint,
                status,
                labels,
                annotations,
                value(alert.get("startsAt")));
    }

    boolean matches(OpsAlertTriggerRule rule, AlertView alert) {
        return regexMatches(rule.getAlertNameRegex(), alert.alertName())
                && regexMatches(rule.getSeverityRegex(), alert.severity())
                && regexMatches(rule.getServiceRegex(), alert.service())
                && labelsMatch(rule.getMatchLabelsJson(), alert.labels());
    }

    boolean verifySignature(
            OpsAlertTriggerRule rule,
            String payloadJson,
            String timestamp,
            String signature,
            long signatureMaxSkewSeconds) {
        if (!StringUtils.hasText(rule.getWebhookSecret())) {
            return true;
        }
        if (!StringUtils.hasText(payloadJson)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(signature)) {
            return false;
        }
        Long epochSeconds = parseEpochSeconds(timestamp);
        if (epochSeconds == null) {
            return false;
        }
        if (Math.abs(currentEpochSeconds.getAsLong() - epochSeconds)
                > Math.max(30, signatureMaxSkewSeconds)) {
            return false;
        }
        String expected = hmacSha256Hex(
                rule.getWebhookSecret(),
                timestamp.trim() + "." + payloadJson);
        String actual = signature.trim();
        if (actual.regionMatches(true, 0, "sha256=", 0, 7)) {
            actual = actual.substring(7);
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    String dedupKey(String sourceType, OpsAlertTriggerRule rule, String fingerprint) {
        return digest(sourceType + ":" + rule.getId() + ":" + value(fingerprint));
    }

    private boolean labelsMatch(String matchLabelsJson, Map<String, Object> labels) {
        if (!StringUtils.hasText(matchLabelsJson)) {
            return true;
        }
        Map<String, Object> expected = parseJsonMap(matchLabelsJson);
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String actual = value(labels.get(entry.getKey()));
            String wanted = value(entry.getValue());
            if (wanted.startsWith("~")) {
                if (!regexMatches(wanted.substring(1), actual)) {
                    return false;
                }
            } else if (!wanted.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean regexMatches(String regex, String value) {
        if (!StringUtils.hasText(regex)) {
            return true;
        }
        try {
            return Pattern.compile(regex).matcher(value(value)).find();
        } catch (Exception e) {
            log.warn("告警触发规则正则非法 regex={}", regex);
            return false;
        }
    }

    private String stableFallbackFingerprint(Map<String, Object> labels) {
        TreeMap<String, Object> stableLabels = new TreeMap<>();
        if (labels != null) {
            labels.forEach((key, value) -> {
                if (StringUtils.hasText(key)) {
                    stableLabels.put(
                            key,
                            value == null ? "" : String.valueOf(value));
                }
            });
        }
        return digest(JSON.toJSONString(stableLabels));
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Webhook 签名计算失败", e);
        }
    }

    private Long parseEpochSeconds(String timestamp) {
        try {
            long raw = Long.parseLong(timestamp.trim());
            return raw > 9_999_999_999L ? raw / 1000 : raw;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return JSON.parseObject(
                json,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String digest(String value) {
        return DigestUtils.md5DigestAsHex(
                value(value).getBytes(StandardCharsets.UTF_8));
    }

    record AlertView(
            String alertName,
            String severity,
            String service,
            String fingerprint,
            String status,
            Map<String, Object> labels,
            Map<String, Object> annotations,
            String startsAt) {

        AlertView(String alertName, String severity, String service, String fingerprint,
                  String status, Map<String,Object> labels, Map<String,Object> annotations) {
            this(alertName,severity,service,fingerprint,status,labels,annotations,"");
        }

        String annotation(String key) {
            return annotations == null
                    ? ""
                    : Optional.ofNullable(annotations.get(key))
                            .map(String::valueOf)
                            .orElse("");
        }
    }
}
