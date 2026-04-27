package cn.lgs.orbisops.trigger.ops.channel.telegram;

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

class OpsTelegramProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsTelegramChannelConnectionDriver driver;
    private OpsTelegramProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsTelegramChannelConnectionDriver.class);
        adapter = new OpsTelegramProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyImplementedLongPollingCapabilities() {
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
    void configurationRequiresCredentialReferenceAndBotUsernameForMentionMode() {
        when(secrets.isReference("${env:TELEGRAM_BOT_TOKEN}")).thenReturn(true);
        OpsTelegramChannelConfiguration configuration = adapter.configuration(channel(
                "${env:TELEGRAM_BOT_TOKEN}",
                Map.of("botUsername", "@orbisops_bot", "requireMention", true)));

        adapter.validateConfiguration(configuration);

        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
        assertEquals("orbisops_bot", configuration.botUsername());
        assertTrue(configuration.requireMention());
    }

    @Test
    void plaintextCredentialAndMissingMentionUsernameAreRejected() {
        OpsTelegramChannelConfiguration plaintext = adapter.configuration(channel(
                "plain-value", Map.of("botUsername", "orbisops_bot")));
        assertEquals("TELEGRAM_BOT_TOKEN_MUST_USE_CREDENTIAL_REF",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(plaintext)).getMessage());

        when(secrets.isReference("${env:TELEGRAM_BOT_TOKEN}")).thenReturn(true);
        OpsTelegramChannelConfiguration missingUsername = adapter.configuration(channel(
                "${env:TELEGRAM_BOT_TOKEN}", Map.of("requireMention", true)));
        assertEquals("TELEGRAM_BOT_USERNAME_REQUIRED_FOR_MENTION_MODE",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(missingUsername)).getMessage());
    }

    @Test
    void unresolvedCredentialIsBlockedExternalWithoutPretendingReady() {
        when(secrets.isReference("${env:TELEGRAM_BOT_TOKEN}")).thenReturn(true);
        when(secrets.resolve("${env:TELEGRAM_BOT_TOKEN}")).thenReturn("");
        OpsTelegramChannelConfiguration configuration = adapter.configuration(channel(
                "${env:TELEGRAM_BOT_TOKEN}", Map.of("botUsername", "orbisops_bot")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("TELEGRAM_CREDENTIAL_UNAVAILABLE", health.reasonCode());
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-telegram", "project-1", ExecutionBinding.react(), "Telegram", "TELEGRAM",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
