package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsDiscordProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsDiscordChannelConnectionDriver driver;
    private OpsDiscordProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsDiscordChannelConnectionDriver.class);
        adapter = new OpsDiscordProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyImplementedGatewayCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertTrue(adapter.capabilities().supports(ChannelCapability.DIRECT_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.GROUP_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.PROACTIVE_PUSH));
        assertFalse(adapter.capabilities().supports(ChannelCapability.WEBHOOK));
        assertFalse(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
    }

    @Test
    void mentionGateCanOperateWithoutPrivilegedMessageContentIntent() {
        when(secrets.isReference("${env:DISCORD_BOT_CREDENTIAL}")).thenReturn(true);
        OpsDiscordChannelConfiguration configuration = adapter.configuration(channel(
                "${env:DISCORD_BOT_CREDENTIAL}",
                Map.of("requireMention", true, "messageContentIntent", false)));

        adapter.validateConfiguration(configuration);

        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
        assertEquals((1 << 9) | (1 << 12), configuration.gatewayIntents());
    }

    @Test
    void unrestrictedGuildMessagesRequireExplicitMessageContentIntent() {
        when(secrets.isReference("${env:DISCORD_BOT_CREDENTIAL}")).thenReturn(true);
        OpsDiscordChannelConfiguration configuration = adapter.configuration(channel(
                "${env:DISCORD_BOT_CREDENTIAL}",
                Map.of("requireMention", false, "messageContentIntent", false)));

        assertEquals("DISCORD_MESSAGE_CONTENT_INTENT_REQUIRED_WITHOUT_MENTION_GATE",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(configuration)).getMessage());
    }

    @Test
    void unresolvedCredentialIsBlockedExternal() {
        when(secrets.isReference("${env:DISCORD_BOT_CREDENTIAL}")).thenReturn(true);
        when(secrets.resolve("${env:DISCORD_BOT_CREDENTIAL}")).thenReturn("");
        OpsDiscordChannelConfiguration configuration = adapter.configuration(channel(
                "${env:DISCORD_BOT_CREDENTIAL}", Map.of("requireMention", true)));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("DISCORD_CREDENTIAL_UNAVAILABLE", health.reasonCode());
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-discord", "project-1", ExecutionBinding.react(), "Discord", "DISCORD",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
