package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpsDingTalkCardClientTest {

    @Test
    void createsGroupCardWithStreamCallbackAndOpaqueActions() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        OpsDingTalkChannelConfiguration configuration = configuration();
        when(tokens.token(configuration)).thenReturn("access-1");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/card/instances/createAndDeliver"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-acs-dingtalk-access-token", "access-1"))
                .andExpect(content().string(containsString("\"cardTemplateId\":\"template-1.schema\"")))
                .andExpect(content().string(containsString("\"callbackType\":\"STREAM\"")))
                .andExpect(content().string(containsString("\"openSpaceId\":\"dtv1.card//IM_GROUP.cid-1\"")))
                .andExpect(content().string(containsString("\"robotCode\":\"robot-1\"")))
                .andExpect(content().string(containsString("\"primaryActionToken\":\"opaque-approve\"")))
                .andExpect(content().string(containsString("\"secondaryActionToken\":\"opaque-reject\"")))
                .andRespond(withSuccess());
        OpsDingTalkCardClient client = new OpsDingTalkCardClient(tokens, builder.build());

        var receipt = client.create(configuration, outbound("group:cid-1", true));

        assertEquals("DELIVERED", receipt.status());
        org.junit.jupiter.api.Assertions.assertTrue(receipt.providerMessageId().startsWith("orbisops-"));
        server.verify();
    }

    @Test
    void createsDirectCardInRobotSpace() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        OpsDingTalkChannelConfiguration configuration = configuration();
        when(tokens.token(configuration)).thenReturn("access-2");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/card/instances/createAndDeliver"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"openSpaceId\":\"dtv1.card//IM_ROBOT.staff-1\"")))
                .andExpect(content().string(containsString("\"spaceType\":\"IM_ROBOT\"")))
                .andRespond(withSuccess());
        OpsDingTalkCardClient client = new OpsDingTalkCardClient(tokens, builder.build());

        client.create(configuration, outbound("user:staff-1", false));

        server.verify();
    }

    @Test
    void updatesExistingCardByOutTrackIdForRunProgress() {
        OpsDingTalkAccessTokenClient tokens = mock(OpsDingTalkAccessTokenClient.class);
        OpsDingTalkChannelConfiguration configuration = configuration();
        when(tokens.token(configuration)).thenReturn("access-3");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/card/instances"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"outTrackId\":\"card-progress-1\"")))
                .andExpect(content().string(containsString("\"updateCardDataByKey\":true")))
                .andExpect(content().string(containsString("\"markdown\":\"Investigating\"")))
                .andRespond(withSuccess());
        OpsDingTalkCardClient client = new OpsDingTalkCardClient(tokens, builder.build());
        ChannelConversationRef conversation = new ChannelConversationRef(
                "group:cid-1", ChannelConversationRef.ConversationKind.GROUP);

        var receipt = client.update(configuration,
                new ChannelMessageRef("card-progress-1", conversation),
                new ChannelOutboundMessage(conversation, ChannelRichContent.text("Investigating"), List.of(), null));

        assertEquals("UPDATED", receipt.status());
        assertEquals("card-progress-1", receipt.providerMessageId());
        server.verify();
    }

    private ChannelOutboundMessage outbound(String target, boolean actions) {
        ChannelRichContent content = actions
                ? new ChannelRichContent("Approval required", "**Approval required**", List.of(
                        new ChannelInteractiveAction("approve", "Approve", "opaque-approve", ChannelInteractiveAction.ActionStyle.PRIMARY),
                        new ChannelInteractiveAction("reject", "Reject", "opaque-reject", ChannelInteractiveAction.ActionStyle.DANGER)))
                : ChannelRichContent.text("hello");
        return new ChannelOutboundMessage(
                new ChannelConversationRef(target, ChannelConversationRef.ConversationKind.UNKNOWN),
                content, List.of(), null);
    }

    private OpsDingTalkChannelConfiguration configuration() {
        return new OpsDingTalkChannelConfiguration(
                "channel-1", "project-1", "credential-ref", "client-1", "corp-1", "robot-1", "template-1.schema",
                ChannelConnectionMode.LONG_CONNECTION);
    }
}
