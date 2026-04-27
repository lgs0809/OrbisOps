package cn.lgs.orbisops.trigger.ops.channel.qq;

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

class OpsQqChannelConnectionDriverTest {

    private OpsQqChannelConnectionDriver driver;

    @AfterEach
    void closeDriver() {
        if (driver != null) driver.shutdown();
    }

    @Test
    void messageDispatchUsesCanonicalInboundPipeline() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsQqApiClient api = mock(OpsQqApiClient.class);
        when(inbound.receiveProvider(eq("channel-qq"), any())).thenReturn(Map.of("accepted", true));
        driver = new OpsQqChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject message = JSONObject.of(
                "id", "msg-1", "content", "inspect latency",
                "author", JSONObject.of("user_openid", "USER_A"));
        JSONObject payload = JSONObject.of("op", 0, "s", 10, "t", "C2C_MESSAGE_CREATE", "d", message);

        driver.handleGatewayPayload(state, payload.toJSONString());

        verify(inbound).receiveProvider(eq("channel-qq"), any());
        verify(actions, never()).tryExecute(eq("channel-qq"), any());
    }

    @Test
    void buttonDispatchAcknowledgesQqThenUsesUnifiedAuthority() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsQqApiClient api = mock(OpsQqApiClient.class);
        when(actions.tryExecute(eq("channel-qq"), any())).thenReturn(Optional.empty());
        driver = new OpsQqChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject event = JSONObject.of(
                "id", "interaction-1",
                "group_openid", "GROUP_A",
                "group_member_openid", "MEMBER_A",
                "data", JSONObject.of("resolved", JSONObject.of(
                        "button_data", "a".repeat(64), "message_id", "msg-approval")));
        JSONObject payload = JSONObject.of("op", 0, "s", 11, "t", "INTERACTION_CREATE", "d", event);

        driver.handleGatewayPayload(state, payload.toJSONString());

        verify(api).acknowledgeInteraction(configuration(), "interaction-1");
        verify(actions).tryExecute(eq("channel-qq"), any());
        verify(inbound, never()).receiveProvider(eq("channel-qq"), any());
    }

    @Test
    void readyMarksGatewayHealthy() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsQqApiClient api = mock(OpsQqApiClient.class);
        driver = new OpsQqChannelConnectionDriver(inbound, actions, api);
        var state = driver.new GatewaySession(configuration());
        JSONObject payload = JSONObject.of("op", 0, "s", 12, "t", "READY",
                "d", JSONObject.of("session_id", "session-test"));

        driver.handleGatewayPayload(state, payload.toJSONString());

        assertEquals("QQ_GATEWAY_READY", driver.health("channel-qq").reasonCode());
    }

    private OpsQqChannelConfiguration configuration() {
        return new OpsQqChannelConfiguration(
                "channel-qq", "project-1", "102000001", "${env:QQ_APP_SECRET_CREDENTIAL}",
                ChannelConnectionMode.LONG_CONNECTION);
    }
}
