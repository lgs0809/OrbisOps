package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpsDingTalkProactiveMessageClientTest {

    @Test
    void groupTargetUsesGroupMessageApiAndJsonStringMessageParameters() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        when(tokens.token(configuration())).thenReturn("access-1");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/robot/groupMessages/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-acs-dingtalk-access-token", "access-1"))
                .andExpect(content().string(containsString("\"openConversationId\":\"cid-1\"")))
                .andExpect(content().string(containsString("\"robotCode\":\"robot-1\"")))
                .andExpect(content().string(containsString("\"msgKey\":\"sampleMarkdown\"")))
                .andExpect(content().string(containsString("\\\"title\\\":\\\"OrbisOps\\\"")))
                .andRespond(withSuccess("{\"processQueryKey\":\"process-group-1\"}", MediaType.APPLICATION_JSON));
        OpsDingTalkProactiveMessageClient client = new OpsDingTalkProactiveMessageClient(tokens, builder.build());

        var receipt = client.send(configuration(), outbound("group:cid-1"));

        assertEquals("process-group-1", receipt.providerMessageId());
        server.verify();
    }

    @Test
    void userTargetUsesOneToOneBatchApiWithSingleStableUserId() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        when(tokens.token(configuration())).thenReturn("access-2");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-acs-dingtalk-access-token", "access-2"))
                .andExpect(content().string(containsString("\"userIds\":[\"staff-1\"]")))
                .andExpect(content().string(containsString("\"robotCode\":\"robot-1\"")))
                .andRespond(withSuccess("{\"processQueryKey\":\"process-user-1\"}", MediaType.APPLICATION_JSON));
        OpsDingTalkProactiveMessageClient client = new OpsDingTalkProactiveMessageClient(tokens, builder.build());

        var receipt = client.send(configuration(), outbound("user:staff-1"));

        assertEquals("process-user-1", receipt.providerMessageId());
        server.verify();
    }

    @Test
    void rejectsAmbiguousTargetBeforeRequestingAccessToken() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        OpsDingTalkProactiveMessageClient client = new OpsDingTalkProactiveMessageClient(
                tokens, RestClient.builder().baseUrl("https://api.dingtalk.com").build());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client.send(configuration(), outbound("cid-without-kind")));

        assertEquals("DINGTALK_TARGET_INVALID", failure.getMessage());
    }

    private ChannelOutboundMessage outbound(String target) {
        return new ChannelOutboundMessage(
                new ChannelConversationRef(target, ChannelConversationRef.ConversationKind.UNKNOWN),
                new ChannelRichContent("hello", "**hello**", List.of()), List.of(), null);
    }

    private OpsDingTalkChannelConfiguration configuration() {
        return new OpsDingTalkChannelConfiguration(
                "channel-1", "project-1", "credential-ref", "client-1", "corp-1", "robot-1", "template-1.schema",
                ChannelConnectionMode.LONG_CONNECTION);
    }
}
