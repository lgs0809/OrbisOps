package cn.lgs.orbisops.trigger.ops.channel.qq;

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

class OpsQqProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsQqChannelConnectionDriver driver;
    private OpsQqProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsQqChannelConnectionDriver.class);
        adapter = new OpsQqProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyImplementedC2cGroupGatewayCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.DIRECT_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.GROUP_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertTrue(adapter.capabilities().supports(ChannelCapability.PROACTIVE_PUSH));
        assertFalse(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertFalse(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.WEBHOOK));
    }

    @Test
    void requiresPublicAppIdAndSecretReference() {
        when(secrets.isReference("${env:QQ_APP_SECRET_CREDENTIAL}")).thenReturn(true);
        OpsQqChannelConfiguration configuration = adapter.configuration(channel(
                "${env:QQ_APP_SECRET_CREDENTIAL}", Map.of("appId", "102000001")));

        adapter.validateConfiguration(configuration);

        assertEquals("102000001", configuration.appId());
        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
        assertEquals((1 << 25) | (1 << 26), configuration.gatewayIntents());

        OpsQqChannelConfiguration plaintext = adapter.configuration(channel(
                "plain-value", Map.of("appId", "102000001")));
        assertEquals("QQ_APP_SECRET_MUST_USE_CREDENTIAL_REF",
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfiguration(plaintext)).getMessage());
    }

    @Test
    void unresolvedCredentialIsBlockedExternal() {
        when(secrets.isReference("${env:QQ_APP_SECRET_CREDENTIAL}")).thenReturn(true);
        when(secrets.resolve("${env:QQ_APP_SECRET_CREDENTIAL}")).thenReturn("");
        OpsQqChannelConfiguration configuration = adapter.configuration(channel(
                "${env:QQ_APP_SECRET_CREDENTIAL}", Map.of("appId", "102000001")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("QQ_CREDENTIAL_UNAVAILABLE", health.reasonCode());
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-qq", "project-1", ExecutionBinding.react(), "QQ", "QQ",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
