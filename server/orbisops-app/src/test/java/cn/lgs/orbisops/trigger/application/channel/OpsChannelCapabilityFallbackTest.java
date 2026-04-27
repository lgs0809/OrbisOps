package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.channel.discord.OpsDiscordChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.discord.OpsDiscordProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.qq.OpsQqChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.qq.OpsQqProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.telegram.OpsTelegramChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.telegram.OpsTelegramProviderAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChannelCapabilityFallbackTest {

    @Test
    void phase3ProvidersDriveMessageUpdateFallbackFromRealCapabilities() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        ChannelProviderAdapter<?> telegram = new OpsTelegramProviderAdapter(
                secrets, mock(OpsTelegramChannelConnectionDriver.class));
        ChannelProviderAdapter<?> discord = new OpsDiscordProviderAdapter(
                secrets, mock(OpsDiscordChannelConnectionDriver.class));
        ChannelProviderAdapter<?> qq = new OpsQqProviderAdapter(
                secrets, mock(OpsQqChannelConnectionDriver.class));

        OpsChannelOutboundDeliveryAdapter delivery = new OpsChannelOutboundDeliveryAdapter(
                providerOf(telegram, discord, qq));

        assertTrue(delivery.supportsMessageUpdate(channel("TELEGRAM")));
        assertTrue(delivery.supportsMessageUpdate(channel("DISCORD")));
        assertFalse(delivery.supportsMessageUpdate(channel("QQ")));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChannelProviderAdapter<?>> providerOf(ChannelProviderAdapter<?>... adapters) {
        ObjectProvider<ChannelProviderAdapter<?>> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(ignored -> Stream.of(adapters));
        return provider;
    }

    private ChannelRecord channel(String type) {
        return new ChannelRecord(
                "channel-" + type.toLowerCase(),
                "project-1",
                ExecutionBinding.react(),
                type,
                type,
                "${env:CHANNEL_PROVIDER_SECRET}",
                Map.of(),
                ChannelAccessPolicy.DENY_UNKNOWN,
                ChannelStatus.ACTIVE,
                "admin",
                null,
                null);
    }
}
