package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsDiscordChannelConnectionDriverTest {

    private OpsDiscordChannelConnectionDriver driver;

    @AfterEach
    void closeDriver() {
        if (driver != null) driver.shutdown();
    }

    @Test
    void messageDispatchUsesCanonicalInboundPipeline() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsDiscordApiClient api = mock(OpsDiscordApiClient.class);
        when(inbound.receiveProvider(eq("channel-discord"), any())).thenReturn(Map.of("accepted", true));
        driver = new OpsDiscordChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject message = JSONObject.of(
                "id", "100", "channel_id", "200", "content", "inspect latency",
                "author", JSONObject.of("id", "300", "username", "alice", "bot", false),
                "mentions", java.util.List.of());
        JSONObject payload = JSONObject.of("op", 0, "s", 10, "t", "MESSAGE_CREATE", "d", message);

        driver.handleGatewayPayload(state, payload.toJSONString());

        verify(inbound).receiveProvider(eq("channel-discord"), any());
        verify(actions, never()).tryExecute(eq("channel-discord"), any());
    }

    @Test
    void componentDispatchAcknowledgesTransportThenUsesUnifiedAuthority() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsDiscordApiClient api = mock(OpsDiscordApiClient.class);
        when(actions.tryExecute(eq("channel-discord"), any())).thenReturn(Optional.empty());
        driver = new OpsDiscordChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject interaction = new JSONObject();
        interaction.put("id", "500");
        interaction.put("type", 3);
        interaction.put("channel_id", "200");
        interaction.put("guild_id", "400");
        interaction.put("token", "test-interaction-credential");
        interaction.put("data", JSONObject.of("component_type", 2, "custom_id", "a".repeat(64)));
        interaction.put("member", JSONObject.of("user", JSONObject.of("id", "300", "username", "approver")));
        interaction.put("message", JSONObject.of("id", "100", "channel_id", "200"));
        JSONObject payload = JSONObject.of("op", 0, "s", 11, "t", "INTERACTION_CREATE", "d", interaction);

        driver.handleGatewayPayload(state, payload.toJSONString());

        verify(api).deferComponent("500", "test-interaction-credential");
        verify(actions).tryExecute(eq("channel-discord"), any());
        verify(inbound, never()).receiveProvider(eq("channel-discord"), any());
    }

    @Test
    void readyDispatchMarksConnectionReadyWithoutCallingBusinessRuntime() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsDiscordApiClient api = mock(OpsDiscordApiClient.class);
        driver = new OpsDiscordChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject ready = JSONObject.of(
                "session_id", "session-test",
                "resume_gateway_url", "wss://gateway.discord.gg",
                "user", JSONObject.of("id", "999", "username", "orbisops"));
        JSONObject payload = JSONObject.of("op", 0, "s", 12, "t", "READY", "d", ready);

        driver.handleGatewayPayload(state, payload.toJSONString());

        assertEquals("DISCORD_GATEWAY_READY", driver.health("channel-discord").reasonCode());
        verify(inbound, never()).receiveProvider(eq("channel-discord"), any());
        verify(actions, never()).tryExecute(eq("channel-discord"), any());
    }

    private OpsDiscordChannelConfiguration configuration() {
        return new OpsDiscordChannelConfiguration(
                "channel-discord", "project-1", "${env:DISCORD_BOT_CREDENTIAL}",
                ChannelConnectionMode.LONG_CONNECTION, false, true);
    }
}
