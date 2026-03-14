package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChannelProtocolCatalogAdapterTest {

    @Test
    void exposesInstalledSlackAdapterAsNativeLongConnectionProvider() {
        @SuppressWarnings("unchecked")
        ChannelProviderAdapter<?> slack = mock(ChannelProviderAdapter.class);
        when(slack.type()).thenReturn(ChannelType.SLACK);
        when(slack.capabilities()).thenReturn(ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.MESSAGE_UPDATE,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.LONG_CONNECTION));

        OpsChannelProtocolCatalogAdapter catalog = new OpsChannelProtocolCatalogAdapter(providerOf(slack));
        var descriptor = catalog.supportedTypes().get(0);

        assertEquals(ChannelType.SLACK, descriptor.type());
        assertEquals("Slack", descriptor.displayName());
        assertTrue(descriptor.supportsInbound());
        assertTrue(descriptor.supportsOutbound());
        assertEquals(java.util.Set.of(ChannelConnectionMode.LONG_CONNECTION), descriptor.connectionModes());
        assertTrue(descriptor.capabilitySet().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(descriptor.capabilitySet().supports(ChannelCapability.INTERACTIVE_ACTIONS));
    }

    @Test
    void exposesInstalledDingTalkAdapterWithNativeCardUpdateAndInteractiveCapabilities() {
        @SuppressWarnings("unchecked")
        ChannelProviderAdapter<?> dingTalk = mock(ChannelProviderAdapter.class);
        when(dingTalk.type()).thenReturn(ChannelType.DINGTALK);
        when(dingTalk.capabilities()).thenReturn(ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.MESSAGE_UPDATE,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.LONG_CONNECTION,
                ChannelCapability.PROACTIVE_PUSH));

        OpsChannelProtocolCatalogAdapter catalog = new OpsChannelProtocolCatalogAdapter(providerOf(dingTalk));
        var descriptor = catalog.supportedTypes().get(0);

        assertEquals(ChannelType.DINGTALK, descriptor.type());
        assertEquals("DingTalk", descriptor.displayName());
        assertTrue(descriptor.supportsInbound());
        assertTrue(descriptor.supportsOutbound());
        assertEquals(java.util.Set.of(ChannelConnectionMode.LONG_CONNECTION), descriptor.connectionModes());
        assertTrue(descriptor.capabilitySet().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(descriptor.capabilitySet().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(descriptor.capabilitySet().supports(ChannelCapability.PROACTIVE_PUSH));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChannelProviderAdapter<?>> providerOf(ChannelProviderAdapter<?> adapter) {
        ObjectProvider<ChannelProviderAdapter<?>> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(adapter));
        return provider;
    }
}
