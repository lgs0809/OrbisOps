package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAttachment;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelMessage;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/channels")
public class OpsChannelWebhookController {

    private final ReceiveChannelMessageUseCase receiveChannelMessage;

    public OpsChannelWebhookController(ReceiveChannelMessageUseCase receiveChannelMessage) {
        this.receiveChannelMessage = receiveChannelMessage;
    }

    @PostMapping("/{channelId}/webhook")
    public Response<Map<String, Object>> webhook(@PathVariable("channelId") String channelId,
                                                  @RequestHeader("X-Ops-Timestamp") String timestamp,
                                                  @RequestHeader("X-Ops-Signature") String signature,
                                                  @RequestBody OpsChannelMessage message) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(receiveChannelMessage.receive(
                        new ChannelModels.Receive(channelId, inbound(message), timestamp, signature)))
                .build();
    }

    private ChannelModels.InboundMessage inbound(OpsChannelMessage message) {
        List<ChannelModels.Attachment> attachments = message.attachments().stream()
                .map(this::attachment)
                .toList();
        ChannelModels.Action action = message.action() == null ? null : new ChannelModels.Action(
                message.action().actionId(), message.action().actionType(), message.action().value(),
                message.action().parameters());
        return new ChannelModels.InboundMessage(message.externalMessageId(), message.externalConversationId(),
                message.senderId(), message.text(), message.timestamp(), message.metadata(), message.messageType(),
                attachments, action);
    }

    private ChannelModels.Attachment attachment(OpsChannelAttachment value) {
        return new ChannelModels.Attachment(value.attachmentId(), value.fileName(), value.mediaType(), value.sizeBytes(),
                value.contentRef(), value.contentHash());
    }
}
