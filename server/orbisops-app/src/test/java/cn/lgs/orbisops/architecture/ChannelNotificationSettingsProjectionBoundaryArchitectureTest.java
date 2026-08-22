package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelNotificationSettingsProjectionBoundaryArchitectureTest {

    private static final String CHANNEL =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/channel/";
    private static final String CONFIGURATION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/channel/"
                    + "OpsChannelNotificationConfiguration.java";

    @Test
    void configurationRecordAndMetadataProjectionHaveExplicitOwners() throws IOException {
        String service = read(CHANNEL + "OpsChannelNotificationService.java");
        String assembly = read(CHANNEL + "OpsChannelNotificationAssembly.java");
        String settings = read(CHANNEL + "OpsChannelNotificationSettings.java");
        String configuration = read(CONFIGURATION);
        String recordFactory = read(CHANNEL + "OpsChannelOutboxRecordFactory.java");
        String viewMapper = read(CHANNEL + "OpsChannelOutboxViewMapper.java");
        String codec = read(CHANNEL + "OpsChannelOutboxMetadataCodec.java");

        assertAll(
                () -> assertFalse(service.contains("OpsChannelNotificationSettings")),
                () -> assertFalse(service.contains("OpsChannelOutboxRecordFactory")),
                () -> assertTrue(service.contains("ChannelNotificationUseCase notificationUseCase")),
                () -> assertTrue(assembly.contains("OpsChannelNotificationSettings settings")),
                () -> assertTrue(assembly.contains("OpsChannelOutboxRecordFactory opsChannelOutboxRecordFactory")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("orbisops.channel.notification.")),
                () -> assertFalse(service.contains("new ChannelOutboxRecord(")),
                () -> assertFalse(service.contains("JSON.")),
                () -> assertTrue(settings.contains("DEFAULT_MAX_MESSAGE_CHARS = 12_000")),
                () -> assertTrue(settings.contains("DEFAULT_MAX_ATTEMPTS = 8")),
                () -> assertTrue(settings.contains("DEFAULT_LEASE_SECONDS = 120")),
                () -> assertTrue(settings.contains("Math.max(500, maxMessageChars)")),
                () -> assertTrue(settings.contains("Math.max(1, Math.min(maxAttempts, 20))")),
                () -> assertTrue(settings.contains("Math.max(15, leaseSeconds)")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.channel.notification.max-message-chars:12000}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.channel.notification.outbox.max-attempts:8}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.channel.notification.outbox.lease-seconds:120}")),
                () -> assertTrue(recordFactory.contains("new ChannelOutboxRecord(")),
                () -> assertTrue(recordFactory.contains("channel-notify:")),
                () -> assertTrue(recordFactory.contains("channel-reply:")),
                () -> assertTrue(recordFactory.contains("metadataCodec.encode(metadata)")),
                () -> assertFalse(recordFactory.contains("JSON.")),
                () -> assertTrue(viewMapper.contains("metadataCodec.decode(metadataJson)")),
                () -> assertFalse(viewMapper.contains("JSON.")),
                () -> assertTrue(codec.contains("JSON.toJSONString(")),
                () -> assertTrue(codec.contains("JSON.parseObject(")),
                () -> assertFalse(codec.contains("IChannelOutboxRepository")),
                () -> assertFalse(codec.contains("ChannelQueryService")));
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
