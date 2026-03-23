package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsTelegramChannelConnectionDriverTest {

    private OpsTelegramChannelConnectionDriver driver;

    @AfterEach
    void closeDriver() {
        if (driver != null) driver.shutdown();
    }

    @Test
    void pollOnceAdvancesOffsetAndRoutesInboundThroughCanonicalUseCase() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsTelegramBotApiClient client = mock(OpsTelegramBotApiClient.class);
        OpsTelegramChannelConfiguration configuration = configuration(false);
        when(client.poll(configuration, 100L)).thenReturn(List.of(
                JSON.parseObject("""
                        {"update_id": 101, "message": {"message_id": 7,
                          "from": {"id": 42, "username": "alice", "is_bot": false},
                          "chat": {"id": 42, "type": "private"}, "text": "inspect latency"}}
                        """),
                JSON.parseObject("""
                        {"update_id": 104, "message": {"message_id": 8,
                          "from": {"id": 43, "username": "bob", "is_bot": false},
                          "chat": {"id": 43, "type": "private"}, "text": "inspect errors"}}
                        """)));
        when(inbound.receiveProvider(eq("channel-telegram"), any())).thenReturn(Map.of("accepted", true));
        driver = new OpsTelegramChannelConnectionDriver(inbound, actions, client);

        long nextOffset = driver.pollOnce(configuration, 100L);

        assertEquals(105L, nextOffset);
        verify(inbound, org.mockito.Mockito.times(2)).receiveProvider(eq("channel-telegram"), any());
        verify(actions, never()).tryExecute(eq("channel-telegram"), any());
    }

    @Test
    void pollOnceRoutesCallbackToUnifiedDispatcherAndKeepsProviderAuthorityNeutral() {
        ReceiveChannelMessageUseCase inbound = mock(ReceiveChannelMessageUseCase.class);
        OpsChannelInteractiveActionDispatcher actions = mock(OpsChannelInteractiveActionDispatcher.class);
        OpsTelegramBotApiClient client = mock(OpsTelegramBotApiClient.class);
        OpsTelegramChannelConfiguration configuration = configuration(false);
        String actionValue = "a".repeat(64);
        when(client.poll(configuration, 0L)).thenReturn(List.of(JSON.parseObject("""
                {"update_id": 205, "callback_query": {
                  "id": "callback-205", "from": {"id": 77, "username": "approver"},
                  "data": "%s",
                  "message": {"message_id": 20, "chat": {"id": 77, "type": "private"}}
                }}
                """.formatted(actionValue))));
        when(actions.tryExecute(eq("channel-telegram"), any())).thenReturn(Optional.empty());
        driver = new OpsTelegramChannelConnectionDriver(inbound, actions, client);

        long nextOffset = driver.pollOnce(configuration, 0L);

        assertEquals(206L, nextOffset);
        verify(actions).tryExecute(eq("channel-telegram"), any());
        verify(client).answerCallback(configuration, "callback-205", "Action unavailable");
        verify(inbound, never()).receiveProvider(eq("channel-telegram"), any());
    }

    private OpsTelegramChannelConfiguration configuration(boolean requireMention) {
        return new OpsTelegramChannelConfiguration(
                "channel-telegram", "project-1", "${env:TELEGRAM_BOT_TOKEN}",
                ChannelConnectionMode.LONG_CONNECTION, requireMention, "orbisops_bot");
    }
}
