package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.channel.dingtalk.OpsDingTalkChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.dingtalk.OpsDingTalkProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.discord.OpsDiscordChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.discord.OpsDiscordProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.feishu.OpsFeishuChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.feishu.OpsFeishuProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.qq.OpsQqChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.qq.OpsQqProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.slack.OpsSlackChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.slack.OpsSlackProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.telegram.OpsTelegramChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.telegram.OpsTelegramProviderAdapter;
import cn.lgs.orbisops.trigger.ops.channel.wecom.OpsWeComChannelConnectionDriver;
import cn.lgs.orbisops.trigger.ops.channel.wecom.OpsWeComProviderAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared transport contract for providers that advertise only native long-connection ingress.
 *
 * <p>The protocol catalog derives install/setup choices from capabilities. Therefore a provider
 * that does not advertise WEBHOOK must not silently accept HYBRID configuration: doing so makes
 * API callers able to persist a transport mode that the runtime cannot actually serve.</p>
 */
class ChannelLongConnectionProviderContractTest {

    @ParameterizedTest(name = "{0} advertises one real inbound transport")
    @MethodSource("providers")
    void longConnectionOnlyProvidersDoNotAdvertiseWebhook(ProviderCase provider) {
        assertTrue(provider.adapter().capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertFalse(provider.adapter().capabilities().supports(ChannelCapability.WEBHOOK));
    }

    @ParameterizedTest(name = "{0} rejects HYBRID when webhook ingress is absent")
    @MethodSource("providers")
    void longConnectionOnlyProvidersRejectHybridConfiguration(ProviderCase provider) {
        assertThrows(IllegalArgumentException.class,
                () -> validateCaptured(provider.adapter(), provider.hybridChannel()));
    }

    private static Stream<ProviderCase> providers() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.isReference(anyString())).thenReturn(true);

        return Stream.of(
                provider(ChannelType.FEISHU,
                        new OpsFeishuProviderAdapter(secrets, mock(OpsFeishuChannelConnectionDriver.class)),
                        Map.of("appId", "cli_demo")),
                provider(ChannelType.WECOM,
                        new OpsWeComProviderAdapter(secrets, mock(OpsWeComChannelConnectionDriver.class)),
                        Map.of("botId", "bot-demo")),
                provider(ChannelType.DINGTALK,
                        new OpsDingTalkProviderAdapter(secrets, mock(OpsDingTalkChannelConnectionDriver.class)),
                        Map.of(
                                "clientId", "ding-demo",
                                "corpId", "corp-demo",
                                "robotCode", "robot-demo",
                                "cardTemplateId", "orbisops.approval")),
                provider(ChannelType.SLACK,
                        new OpsSlackProviderAdapter(secrets, mock(OpsSlackChannelConnectionDriver.class)),
                        Map.of("appCredentialRef", "${env:SLACK_APP_TOKEN}")),
                provider(ChannelType.TELEGRAM,
                        new OpsTelegramProviderAdapter(secrets, mock(OpsTelegramChannelConnectionDriver.class)),
                        Map.of("botUsername", "orbisops_bot", "requireMention", true)),
                provider(ChannelType.DISCORD,
                        new OpsDiscordProviderAdapter(secrets, mock(OpsDiscordChannelConnectionDriver.class)),
                        Map.of("requireMention", true, "messageContentIntent", false)),
                provider(ChannelType.QQ,
                        new OpsQqProviderAdapter(secrets, mock(OpsQqChannelConnectionDriver.class)),
                        Map.of("appId", "102000000"))
        );
    }

    private static ProviderCase provider(ChannelType type,
                                         ChannelProviderAdapter<?> adapter,
                                         Map<String, Object> providerConfig) {
        Map<String, Object> config = new LinkedHashMap<>(providerConfig);
        config.put("connectionMode", "HYBRID");
        ChannelRecord channel = new ChannelRecord(
                "contract-" + type.name().toLowerCase(),
                "project-contract",
                ExecutionBinding.react(),
                type.name(),
                type.name(),
                "${env:CHANNEL_PROVIDER_SECRET}",
                config,
                ChannelAccessPolicy.DENY_UNKNOWN,
                ChannelStatus.ACTIVE,
                "contract-test",
                null,
                null);
        return new ProviderCase(type, adapter, channel);
    }

    private static <C extends ChannelProviderConfiguration> void validateCaptured(ChannelProviderAdapter<C> adapter,
                                                                                  ChannelRecord channel) {
        C configuration = adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
    }

    private record ProviderCase(ChannelType type,
                                ChannelProviderAdapter<?> adapter,
                                ChannelRecord hybridChannel) {
        @Override
        public String toString() {
            return type.name();
        }
    }
}
