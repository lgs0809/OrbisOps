package cn.lgs.orbisops.trigger.ops.channel.slack;

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

class OpsSlackProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsSlackChannelConnectionDriver driver;
    private OpsSlackProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsSlackChannelConnectionDriver.class);
        adapter = new OpsSlackProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyImplementedNativeCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertTrue(adapter.capabilities().supports(ChannelCapability.DIRECT_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.GROUP_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.WEBHOOK));
    }

    @Test
    void configurationRequiresBotAndAppCredentialReferencesAndDefaultsToSocketMode() {
        when(secrets.isReference("${env:SLACK_BOT_TOKEN}")).thenReturn(true);
        when(secrets.isReference("${env:SLACK_APP_TOKEN}")).thenReturn(true);
        OpsSlackChannelConfiguration configuration = adapter.configuration(channel(
                "${env:SLACK_BOT_TOKEN}",
                Map.of("appCredentialRef", "${env:SLACK_APP_TOKEN}")));

        adapter.validateConfiguration(configuration);

        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
        assertTrue(configuration.requireMention());
    }

    @Test
    void plaintextAppTokenIsRejectedEvenWhenBotTokenUsesCredentialRef() {
        when(secrets.isReference("${env:SLACK_BOT_TOKEN}")).thenReturn(true);
        OpsSlackChannelConfiguration configuration = adapter.configuration(channel(
                "${env:SLACK_BOT_TOKEN}", Map.of("appCredentialRef", "plaintext-app-token")));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> adapter.validateConfiguration(configuration));

        assertEquals("SLACK_APP_TOKEN_MUST_USE_CREDENTIAL_REF", failure.getMessage());
    }

    @Test
    void unresolvedEitherCredentialIsBlockedExternalInsteadOfReady() {
        when(secrets.isReference("${env:SLACK_BOT_TOKEN}")).thenReturn(true);
        when(secrets.isReference("${env:SLACK_APP_TOKEN}")).thenReturn(true);
        when(secrets.resolve("${env:SLACK_BOT_TOKEN}")).thenReturn("test-bot-token");
        when(secrets.resolve("${env:SLACK_APP_TOKEN}")).thenReturn("");
        OpsSlackChannelConfiguration configuration = adapter.configuration(channel(
                "${env:SLACK_BOT_TOKEN}",
                Map.of("appCredentialRef", "${env:SLACK_APP_TOKEN}")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("SLACK_CREDENTIAL_UNAVAILABLE", health.reasonCode());
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-slack", "project-1", ExecutionBinding.react(), "Slack", "SLACK",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
