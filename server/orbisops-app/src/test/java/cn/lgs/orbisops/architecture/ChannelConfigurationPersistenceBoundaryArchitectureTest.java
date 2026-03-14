package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelConfigurationPersistenceBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/channel/model/ChannelRecord.java";
    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/channel/service/ChannelConfigurationPolicy.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/channel/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/channel/";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsChannelRepository.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/channel/OpsChannelApplicationConfiguration.java";

    @Test
    void domainOwnsTypedImmutableConfigurationInsteadOfPersistenceJson() throws IOException {
        String channel = read(DOMAIN);
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(channel.contains("ExecutionBinding inboundExecution")),
                () -> assertFalse(channel.contains("String agentId")),
                () -> assertTrue(channel.contains("ChannelAccessPolicy accessPolicy")),
                () -> assertTrue(channel.contains("ChannelStatus status")),
                () -> assertTrue(channel.contains("Map<String, Object> config")),
                () -> assertTrue(channel.contains("Collections.unmodifiableMap")),
                () -> assertTrue(policy.contains("ChannelRecord create(ChannelRecord candidate)")),
                () -> assertTrue(policy.contains("ChannelRecord update(ChannelRecord current, ChannelRecord candidate)")),
                () -> assertFalse(policy.contains("Map<String, Object>")),
                () -> assertFalse(policy.contains(".get(\"")),
                () -> assertFalse(channel.contains("configJson")),
                () -> assertFalse(channel.contains("com.alibaba.fastjson")),
                () -> assertFalse(channel.contains("org.springframework")),
                () -> assertFalse(channel.contains("config_json")));
    }

    @Test
    void applicationConsumesTypedCommandsAndNeverParsesManagementProtocolKeys() throws IOException {
        String management = read(APPLICATION + "ChannelManagementApplicationService.java");
        String models = read(APPLICATION + "ChannelModels.java");
        String inbound = read(APPLICATION + "ChannelInboundProcessManager.java");
        String outbound = read(APPLICATION + "ChannelOutboundApplicationService.java");
        String outboundMetadata = read(APPLICATION + "ChannelOutboundMetadata.java");
        String deliveryConfiguration = read(APPLICATION + "ChannelDeliveryConfiguration.java");
        String encoder = read(APPLICATION + "ChannelOutboundPayloadEncoder.java");
        String query = read(APPLICATION + "ChannelQueryService.java");

        assertAll(
                () -> assertTrue(models.contains("record ConfigurationMutation")),
                () -> assertTrue(models.contains("record IdentityBinding")),
                () -> assertTrue(management.contains("ChannelModels.ConfigurationMutation")),
                () -> assertTrue(management.contains("ChannelModels.IdentityBinding")),
                () -> assertFalse(management.contains("ChannelModels.Mutation")),
                () -> assertFalse(management.contains("command.request()")),
                () -> assertFalse(management.contains(".get(\"agentBindingMode\")")),
                () -> assertFalse(management.contains(".get(\"agentVersion\")")),
                () -> assertFalse(management.contains(".get(\"projectId\")")),
                () -> assertFalse(management.contains(".get(\"status\")")),
                () -> assertFalse(management.contains(".get(\"type\")")),
                () -> assertTrue(inbound.contains("ChannelStatus.ACTIVE")),
                () -> assertTrue(inbound.contains("ExecutionVersionPolicy.PINNED_VERSION")),
                () -> assertTrue(inbound.contains("CHANNEL_INBOUND_DISABLED")),
                () -> assertTrue(outbound.contains("ChannelDeliveryReceipt delivery")),
                () -> assertTrue(outbound.contains("new ChannelOutboundMessage(")),
                () -> assertTrue(outbound.contains("safeDelivery.delivered()")),
                () -> assertFalse(outbound.contains("safeDelivery.get(\"delivered\")")),
                () -> assertFalse(outbound.contains("ChannelDeliveryResult")),
                () -> assertTrue(inbound.contains(
                        "ChannelDeliveryConfiguration.from(channel.config()).replyConfigured()")),
                () -> assertFalse(inbound.contains(".get(\"outboundUrl\")")),
                () -> assertTrue(outbound.contains("ChannelOutboundMetadata.from(command.metadata())")),
                () -> assertTrue(outbound.contains("deliveryMetadata.toProtocolMap()")),
                () -> assertFalse(outbound.contains("ChannelDeliveryConfiguration.from(channel.config()).protocolConfig()")),
                () -> assertFalse(outbound.contains("metadata.get(\"runId\")")),
                () -> assertFalse(outbound.contains("metadata.get(\"sessionId\")")),
                () -> assertFalse(outbound.contains("metadata.get(\"outboxId\")")),
                () -> assertTrue(outboundMetadata.contains("record ChannelOutboundMetadata(")),
                () -> assertTrue(outboundMetadata.contains("toProtocolMap()")),
                () -> assertTrue(deliveryConfiguration.contains("record ChannelDeliveryConfiguration(")),
                () -> assertTrue(deliveryConfiguration.contains("replyConfigured()")),
                () -> assertTrue(query.contains("data.put(\"config\", row.config())")),
                () -> assertFalse(management.contains("ChannelJsonCodecPort")),
                () -> assertFalse(inbound.contains("ChannelJsonCodecPort")),
                () -> assertFalse(query.contains("ChannelJsonCodecPort")),
                () -> assertFalse(outbound.contains("ChannelJsonCodecPort")),
                () -> assertTrue(outbound.contains("new ChannelOutboundPayloadEncoder()")),
                () -> assertTrue(outbound.contains("payloadEncoder.pending(")),
                () -> assertTrue(outbound.contains("payloadEncoder.delivery(")),
                () -> assertTrue(encoder.contains("final class ChannelOutboundPayloadEncoder")),
                () -> assertTrue(encoder.contains("CanonicalJson.stringifyPreservingOrder")),
                () -> assertFalse(encoder.contains("com.alibaba.fastjson")),
                () -> assertFalse(encoder.contains("com.fasterxml.jackson")),
                () -> assertFalse(encoder.contains("org.springframework")),
                () -> assertFalse(encoder.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(management.contains("configJson")),
                () -> assertFalse(inbound.contains("configJson")),
                () -> assertFalse(outbound.contains("configJson")),
                () -> assertFalse(query.contains("configJson")));
    }

    @Test
    void triggerOwnsProtocolMapTranslationAndInfrastructureOwnsJsonColumnEncoding() throws IOException {
        String mapper = read(TRIGGER + "OpsChannelCommandMapper.java");
        String outboundAdapter = read(TRIGGER + "OpsChannelOutboundDeliveryAdapter.java");
        String repository = read(REPOSITORY);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(mapper.contains("Map<String, Object> request")),
                () -> assertTrue(mapper.contains("ExecutionVersionPolicy::require")),
                () -> assertTrue(mapper.contains("ExecutionType.require")),
                () -> assertTrue(mapper.contains("ChannelStatus::require")),
                () -> assertTrue(mapper.contains("ChannelAccessPolicy::require")),
                () -> assertTrue(outboundAdapter.contains("ChannelProviderAdapter<?>")),
                () -> assertTrue(outboundAdapter.contains("ChannelDeliveryReceipt")),
                () -> assertTrue(outboundAdapter.contains("sendCaptured")),
                () -> assertFalse(outboundAdapter.contains("Map<String, Object> configuration")),
                () -> assertTrue(repository.contains("config_json")),
                () -> assertTrue(repository.contains("encodeConfig(channel.config())")),
                () -> assertTrue(repository.contains("decodeConfig(rs.getString(\"config_json\"))")),
                () -> assertTrue(repository.contains("execution.versionPolicy().name()")),
                () -> assertTrue(repository.contains("execution_kind")),
                () -> assertTrue(repository.contains("channel.status().name()")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("JSON.parseObject")),
                () -> assertFalse(configuration.contains("ChannelQueryService(repository, jsonCodec")),
                () -> assertFalse(configuration.contains("ChannelManagementApplicationService(repository, catalogQuery, jsonCodec")),
                () -> assertFalse(configuration.contains("executor, jsonCodec, contentPolicy")));
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
