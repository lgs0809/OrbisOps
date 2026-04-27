package cn.lgs.orbisops.trigger.ops.channel.wechat;

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

class OpsWeChatProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsWeChatOfficialAccountClient client;
    private OpsWeChatProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        client = mock(OpsWeChatOfficialAccountClient.class);
        adapter = new OpsWeChatProviderAdapter(secrets, client);
    }

    @Test
    void exposesOnlyOfficialAccountSecureWebhookCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.DIRECT_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.WEBHOOK));
        assertTrue(adapter.capabilities().supports(ChannelCapability.REPLY_TO_INBOUND));
        assertFalse(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertFalse(adapter.capabilities().supports(ChannelCapability.GROUP_MESSAGES));
        assertFalse(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertFalse(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.PROACTIVE_PUSH));
    }

    @Test
    void configurationRequiresAllSecretsAsReferencesAndWebhookMode() {
        when(secrets.isReference("${env:WECHAT_APP_SECRET}")).thenReturn(true);
        when(secrets.isReference("${env:WECHAT_VERIFY_TOKEN}")).thenReturn(true);
        when(secrets.isReference("${env:WECHAT_AES_KEY}")).thenReturn(true);
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel(Map.of(
                "appId", "wx123",
                "verificationTokenRef", "${env:WECHAT_VERIFY_TOKEN}",
                "encodingAesKeyRef", "${env:WECHAT_AES_KEY}")));

        adapter.validateConfiguration(configuration);
        assertEquals(ChannelConnectionMode.WEBHOOK, configuration.connectionMode());

        OpsWeChatChannelConfiguration hybrid = adapter.configuration(channel(Map.of(
                "appId", "wx123",
                "verificationTokenRef", "${env:WECHAT_VERIFY_TOKEN}",
                "encodingAesKeyRef", "${env:WECHAT_AES_KEY}",
                "connectionMode", "HYBRID")));
        assertEquals("WECHAT_CONNECTION_MODE_UNSUPPORTED:HYBRID",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(hybrid)).getMessage());
    }

    @Test
    void plaintextCallbackSecretIsRejected() {
        when(secrets.isReference("${env:WECHAT_APP_SECRET}")).thenReturn(true);
        when(secrets.isReference("${env:WECHAT_AES_KEY}")).thenReturn(true);
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel(Map.of(
                "appId", "wx123",
                "verificationTokenRef", "plaintext-token",
                "encodingAesKeyRef", "${env:WECHAT_AES_KEY}")));

        assertEquals("WECHAT_VERIFICATION_TOKEN_MUST_USE_CREDENTIAL_REF",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(configuration)).getMessage());
    }

    @Test
    void unresolvedSecretIsBlockedExternalWithoutPretendingReady() {
        validReferences();
        when(secrets.resolve("${env:WECHAT_APP_SECRET}")).thenReturn("app-secret");
        when(secrets.resolve("${env:WECHAT_VERIFY_TOKEN}")).thenReturn("");
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel(Map.of(
                "appId", "wx123",
                "verificationTokenRef", "${env:WECHAT_VERIFY_TOKEN}",
                "encodingAesKeyRef", "${env:WECHAT_AES_KEY}")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("WECHAT_VERIFICATION_TOKEN_UNAVAILABLE", health.reasonCode());
    }

    @Test
    void resolvableOfficialCredentialsStillRequireRealCallbackForReadiness() {
        validReferences();
        when(secrets.resolve("${env:WECHAT_APP_SECRET}")).thenReturn("app-secret");
        when(secrets.resolve("${env:WECHAT_VERIFY_TOKEN}")).thenReturn("verify-token");
        when(secrets.resolve("${env:WECHAT_AES_KEY}")).thenReturn("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel(Map.of(
                "appId", "wx123",
                "verificationTokenRef", "${env:WECHAT_VERIFY_TOKEN}",
                "encodingAesKeyRef", "${env:WECHAT_AES_KEY}")));
        when(client.token(configuration)).thenReturn("access-token");

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.UNKNOWN, health.status());
        assertEquals("WECHAT_CALLBACK_EXTERNAL_VERIFICATION_REQUIRED", health.reasonCode());
    }

    private void validReferences() {
        when(secrets.isReference("${env:WECHAT_APP_SECRET}")).thenReturn(true);
        when(secrets.isReference("${env:WECHAT_VERIFY_TOKEN}")).thenReturn(true);
        when(secrets.isReference("${env:WECHAT_AES_KEY}")).thenReturn(true);
    }

    private ChannelRecord channel(Map<String, Object> config) {
        return new ChannelRecord("channel-wechat", "project-1", ExecutionBinding.react(), "WeChat", "WECHAT",
                "${env:WECHAT_APP_SECRET}", config, ChannelAccessPolicy.DENY_UNKNOWN,
                ChannelStatus.ACTIVE, "admin", null, null);
    }
}
