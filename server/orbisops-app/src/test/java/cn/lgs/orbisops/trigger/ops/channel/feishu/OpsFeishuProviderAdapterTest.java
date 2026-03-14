package cn.lgs.orbisops.trigger.ops.channel.feishu;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsFeishuProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsFeishuChannelConnectionDriver driver;
    private OpsFeishuProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsFeishuChannelConnectionDriver.class);
        adapter = new OpsFeishuProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyImplementedNativeCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertFalse(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
    }

    @Test
    void configurationDefaultsToLongConnectionAndRequiresCredentialReference() {
        when(secrets.isReference("${env:FEISHU_SECRET}")).thenReturn(true);
        OpsFeishuChannelConfiguration configuration = adapter.configuration(channel("${env:FEISHU_SECRET}", Map.of("appId", "cli-test")));
        adapter.validateConfiguration(configuration);

        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
        assertTrue(configuration.requireMention());
    }

    @Test
    void unresolvedCredentialIsBlockedExternalInsteadOfReady() {
        when(secrets.isReference("${env:FEISHU_SECRET}")).thenReturn(true);
        when(secrets.resolve("${env:FEISHU_SECRET}")).thenReturn("");
        OpsFeishuChannelConfiguration configuration = adapter.configuration(channel("${env:FEISHU_SECRET}", Map.of("appId", "cli-test")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("FEISHU_APP_SECRET_UNAVAILABLE", health.reasonCode());
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-feishu", "project-1", ExecutionBinding.react(), "Feishu", "FEISHU",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
