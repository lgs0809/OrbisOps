package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertWebhookProtocolServiceTest {

    private static final long NOW = 1_700_000_000L;

    private final OpsAlertWebhookProtocolService protocol =
            new OpsAlertWebhookProtocolService(() -> NOW);

    @Test
    void extractsAlertmanagerListAndBuildsStableTypedView() {
        Map<Object, Object> firstRaw = new LinkedHashMap<>();
        firstRaw.put("status", "firing");
        firstRaw.put("labels", Map.of(
                "service", "payment",
                "alertname", "HighErrorRate"));
        firstRaw.put("annotations", Map.of("summary", "first message"));
        firstRaw.put("startsAt", "2026-07-15T10:00:00Z");
        firstRaw.put(7, "numeric-key");
        Map<String, Object> payload = Map.of(
                "alerts",
                List.of(firstRaw, "ignored", Map.of(
                        "status", "firing",
                        "labels", Map.of(
                                "alertname", "HighErrorRate",
                                "service", "payment"),
                        "annotations", Map.of("summary", "changed message"),
                        "startsAt", "2026-07-15T10:05:00Z")));

        List<Map<String, Object>> alerts = protocol.extractAlerts(payload);
        OpsAlertWebhookProtocolService.AlertView first = protocol.alertView(alerts.get(0));
        OpsAlertWebhookProtocolService.AlertView second = protocol.alertView(alerts.get(1));

        assertEquals(2, alerts.size());
        assertEquals("numeric-key", alerts.get(0).get("7"));
        assertEquals("HighErrorRate", first.alertName());
        assertEquals("warning", first.severity());
        assertEquals("payment", first.service());
        assertEquals("firing", first.status());
        assertEquals("2026-07-15T10:00:00Z", first.startsAt());
        assertEquals("2026-07-15T10:05:00Z", second.startsAt());
        assertEquals("first message", first.annotation("summary"));
        assertEquals(first.fingerprint(), second.fingerprint());
        assertFalse(first.fingerprint().isBlank());

        assertTrue(protocol.extractAlerts(null).isEmpty());
        assertTrue(protocol.extractAlerts(Map.of()).isEmpty());
        assertEquals(
                List.of(Map.of("alertname", "single")),
                protocol.extractAlerts(Map.of("alertname", "single")));
    }

    @Test
    void matchesRuleRegexAndExactOrRegexLabels() {
        OpsAlertWebhookProtocolService.AlertView alert = protocol.alertView(Map.of(
                "fingerprint", "fp-1",
                "labels", Map.of(
                        "alertname", "HighErrorRate",
                        "severity", "critical",
                        "service", "payment-api",
                        "cluster", "prod-shanghai",
                        "team", "checkout")));
        OpsAlertTriggerRule matching = OpsAlertTriggerRule.builder()
                .alertNameRegex("High.*")
                .severityRegex("critical|warning")
                .serviceRegex("payment")
                .matchLabelsJson("{\"team\":\"checkout\",\"cluster\":\"~prod-.*\"}")
                .build();

        assertTrue(protocol.matches(matching, alert));
        assertFalse(protocol.matches(
                OpsAlertTriggerRule.builder()
                        .matchLabelsJson("{\"team\":\"inventory\"}")
                        .build(),
                alert));
        assertFalse(protocol.matches(
                OpsAlertTriggerRule.builder()
                        .alertNameRegex("[")
                        .build(),
                alert));
    }

    @Test
    void verifiesHmacPrefixMillisecondsAndSkewWithoutNormalizingFailures() throws Exception {
        String payload = "{\"alerts\":[]}";
        String timestamp = String.valueOf(NOW);
        String secret = "secret";
        String signature = hmac(secret, timestamp + "." + payload);
        OpsAlertTriggerRule secured = OpsAlertTriggerRule.builder()
                .webhookSecret(secret)
                .build();

        assertTrue(protocol.verifySignature(
                secured,
                payload,
                timestamp,
                "SHA256=" + signature,
                300));
        assertTrue(protocol.verifySignature(
                secured,
                payload,
                String.valueOf(NOW * 1000),
                hmac(secret, (NOW * 1000) + "." + payload),
                300));
        assertFalse(protocol.verifySignature(
                secured,
                payload,
                String.valueOf(NOW - 31),
                hmac(secret, (NOW - 31) + "." + payload),
                1));
        assertFalse(protocol.verifySignature(secured, payload, "bad", signature, 300));
        assertFalse(protocol.verifySignature(secured, payload, timestamp, "bad", 300));
        assertFalse(protocol.verifySignature(secured, null, timestamp, signature, 300));
        assertTrue(protocol.verifySignature(
                OpsAlertTriggerRule.builder().webhookSecret(" ").build(),
                null,
                null,
                null,
                300));
    }

    @Test
    void dedupKeyIsStableAndScopedBySourceRuleAndFingerprint() {
        OpsAlertTriggerRule rule = OpsAlertTriggerRule.builder().id(7L).build();

        String first = protocol.dedupKey("ALERTMANAGER", rule, "fp-1");
        String same = protocol.dedupKey("ALERTMANAGER", rule, "fp-1");
        String otherFingerprint = protocol.dedupKey("ALERTMANAGER", rule, "fp-2");
        String otherRule = protocol.dedupKey(
                "ALERTMANAGER",
                OpsAlertTriggerRule.builder().id(8L).build(),
                "fp-1");

        assertEquals(first, same);
        assertEquals(32, first.length());
        assertNotEquals(first, otherFingerprint);
        assertNotEquals(first, otherRule);
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return HexFormat.of().formatHex(
                mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
