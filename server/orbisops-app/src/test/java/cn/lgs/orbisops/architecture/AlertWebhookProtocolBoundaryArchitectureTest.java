package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertWebhookProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void alertTriggerDelegatesWebhookParsingMatchingSecurityPersistenceAndSubmission() throws IOException {
        String trigger = read(OPS + "OpsAlertTriggerService.java");
        String protocol = read(OPS + "OpsAlertWebhookProtocolService.java");
        String recorder = read(OPS + "OpsAlertEventRecorder.java");
        String submissions = read(OPS + "OpsAlertRunSubmissionCoordinator.java");

        assertAll(
                () -> assertTrue(trigger.contains(
                        "OpsAlertWebhookProtocolService webhookProtocol")),
                () -> assertTrue(trigger.contains("webhookProtocol.extractAlerts(payload)")),
                () -> assertTrue(trigger.contains("webhookProtocol.alertView(rawAlert)")),
                () -> assertTrue(trigger.contains("webhookProtocol.matches(rule, alert)")),
                () -> assertTrue(trigger.contains("webhookProtocol.verifySignature(")),
                () -> assertTrue(trigger.contains("webhookProtocol.dedupKey(")),
                () -> assertTrue(trigger.contains("aggregation.record(")),
                () -> assertTrue(trigger.contains("OpsAlertEventRecorder events")),
                () -> assertTrue(trigger.contains("OpsAlertRunSubmissionCoordinator submissions")),
                () -> assertTrue(recorder.contains("alertEvents.append(mapper.draft(")),
                () -> assertTrue(submissions.contains("alertOutbox.dispatchIfCapacity(")),
                () -> assertFalse(trigger.contains("TypeReference")),
                () -> assertFalse(trigger.contains("DigestUtils")),
                () -> assertFalse(trigger.contains("SecretKeySpec")),
                () -> assertFalse(trigger.contains("MessageDigest")),
                () -> assertFalse(trigger.contains("HexFormat")),
                () -> assertFalse(trigger.contains("TreeMap")),
                () -> assertFalse(trigger.contains("Pattern.compile")),
                () -> assertFalse(trigger.contains("private boolean matches(")),
                () -> assertFalse(trigger.contains("private boolean verifySignature(")),
                () -> assertFalse(trigger.contains("private String stableFallbackFingerprint(")),
                () -> assertFalse(trigger.contains("private String hmacSha256Hex(")),
                () -> assertFalse(trigger.contains("private Long parseEpochSeconds(")),
                () -> assertFalse(trigger.contains("private String dedupKey(")),
                () -> assertFalse(trigger.contains("record AlertView(")),
                () -> assertTrue(trigger.lines().count() <= 260),
                () -> assertTrue(protocol.contains("LongSupplier currentEpochSeconds")),
                () -> assertTrue(protocol.contains("List<Map<String, Object>> extractAlerts(")),
                () -> assertTrue(protocol.contains("AlertView alertView(")),
                () -> assertTrue(protocol.contains("boolean matches(")),
                () -> assertTrue(protocol.contains("boolean verifySignature(")),
                () -> assertTrue(protocol.contains("String dedupKey(")),
                () -> assertTrue(protocol.contains("record AlertView(")),
                () -> assertTrue(protocol.contains("TypeReference<LinkedHashMap<String, Object>>")),
                () -> assertTrue(protocol.contains("Pattern.compile(")),
                () -> assertTrue(protocol.contains("Mac.getInstance(\"HmacSHA256\")")),
                () -> assertTrue(protocol.contains("MessageDigest.isEqual(")),
                () -> assertTrue(protocol.contains("DigestUtils.md5DigestAsHex(")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Component")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("@Autowired")),
                () -> assertFalse(protocol.contains("AlertAggregationApplicationService")),
                () -> assertFalse(protocol.contains("AlertOutboxApplicationService")),
                () -> assertFalse(protocol.contains("AlertEventApplicationService")),
                () -> assertFalse(protocol.contains("OpsAnalysisRunService")),
                () -> assertTrue(protocol.lines().count() <= 280));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
