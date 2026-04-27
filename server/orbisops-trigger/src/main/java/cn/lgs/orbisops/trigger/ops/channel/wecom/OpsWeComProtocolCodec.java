package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure WeCom WebSocket protocol codec. No sockets, secrets, threads, or runtime calls. */
final class OpsWeComProtocolCodec {

    static final String SUBSCRIBE = "aibot_subscribe";
    static final String HEARTBEAT = "ping";
    static final String CALLBACK = "aibot_msg_callback";
    static final String EVENT_CALLBACK = "aibot_event_callback";
    static final String RESPONSE = "aibot_respond_msg";
    static final String RESPONSE_UPDATE = "aibot_respond_update_msg";
    static final String SEND_MSG = "aibot_send_msg";

    String subscribe(String reqId, String botId, String secret) {
        return frame(SUBSCRIBE, reqId, Map.of("bot_id", required(botId, "WECOM_BOT_ID_REQUIRED"),
                "secret", required(secret, "WECOM_BOT_SECRET_REQUIRED")));
    }

    String heartbeat(String reqId) {
        return frame(HEARTBEAT, reqId, Map.of());
    }

    String proactiveMarkdown(String reqId, String chatId, String content) {
        return frame(SEND_MSG, reqId, Map.of(
                "chatid", required(chatId, "WECOM_CHAT_ID_REQUIRED"),
                "msgtype", "markdown",
                "markdown", Map.of("content", required(content, "WECOM_MESSAGE_CONTENT_REQUIRED"))));
    }

    String approvalCard(String reqId,
                        String chatId,
                        String title,
                        String summary,
                        List<ChannelInteractiveAction> actions) {
        if (actions == null || actions.isEmpty()) throw new IllegalArgumentException("WECOM_CARD_ACTION_REQUIRED");
        List<Map<String, Object>> buttons = actions.stream().map(action -> Map.<String, Object>of(
                "text", required(action.label(), "WECOM_CARD_ACTION_LABEL_REQUIRED"),
                "style", action.style() == ChannelInteractiveAction.ActionStyle.DANGER ? 2 : 1,
                "key", required(action.opaqueActionToken(), "WECOM_CARD_ACTION_TOKEN_REQUIRED"))).toList();
        return frame(SEND_MSG, reqId, Map.of(
                "chatid", required(chatId, "WECOM_CHAT_ID_REQUIRED"),
                "msgtype", "template_card",
                "template_card", Map.of(
                        "card_type", "text_notice",
                        "main_title", Map.of("title", required(title, "WECOM_CARD_TITLE_REQUIRED")),
                        "sub_title_text", required(summary, "WECOM_CARD_SUMMARY_REQUIRED"),
                        "button_list", buttons)));
    }

    String replyMarkdown(String originalReqId, String content) {
        return frame(RESPONSE, originalReqId, Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", required(content, "WECOM_MESSAGE_CONTENT_REQUIRED"))));
    }

    String updateCard(String originalReqId, String taskId, String title) {
        return frame(RESPONSE_UPDATE, originalReqId, Map.of(
                "msgtype", "template_card",
                "template_card", Map.of(
                        "card_type", "text_notice",
                        "main_title", Map.of("title", required(title, "WECOM_CARD_TITLE_REQUIRED")),
                        "task_id", required(taskId, "WECOM_CARD_TASK_ID_REQUIRED"))));
    }

    Optional<InboundFrame> inbound(String raw) {
        JSONObject root = parse(raw);
        String cmd = text(root.get("cmd"));
        if (!CALLBACK.equals(cmd) && !EVENT_CALLBACK.equals(cmd)) return Optional.empty();
        JSONObject headers = object(root.get("headers"));
        JSONObject body = object(root.get("body"));
        String reqId = text(headers.get("req_id"));
        String messageId = required(body.get("msgid"), "WECOM_MESSAGE_ID_REQUIRED");
        String senderId = required(object(body.get("from")).get("userid"), "WECOM_SENDER_ID_REQUIRED");
        String chatType = text(body.get("chattype"));
        boolean group = "group".equalsIgnoreCase(chatType);
        String conversationId = group ? required(body.get("chatid"), "WECOM_CHAT_ID_REQUIRED") : senderId;
        ChannelConversationRef conversation = new ChannelConversationRef(conversationId,
                group ? ChannelConversationRef.ConversationKind.GROUP : ChannelConversationRef.ConversationKind.DIRECT);
        Instant receivedAt = instant(body.get("create_time"));
        if (CALLBACK.equals(cmd)) {
            String content = messageContent(body);
            if (content.isBlank()) return Optional.empty();
            ChannelInboundEnvelope envelope = new ChannelInboundEnvelope(
                    new ChannelMessageRef(messageId, conversation),
                    new ChannelExternalPrincipal(senderId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                    ChannelRichContent.text(content), List.of(), receivedAt, messageId);
            return Optional.of(new InboundFrame(reqId, envelope, text(body.get("response_url")), ""));
        }
        JSONObject event = object(body.get("event"));
        if (!"template_card_event".equalsIgnoreCase(text(event.get("eventtype")))) return Optional.empty();
        JSONObject cardEvent = object(event.get("template_card_event"));
        String eventKey = required(cardEvent.get("event_key"), "WECOM_CARD_EVENT_KEY_REQUIRED");
        String taskId = text(cardEvent.get("task_id"));
        ChannelInteractiveAction action = new ChannelInteractiveAction(
                eventKey, "WeCom card action", eventKey, ChannelInteractiveAction.ActionStyle.DEFAULT);
        ChannelInboundEnvelope envelope = new ChannelInboundEnvelope(
                new ChannelMessageRef(messageId, conversation),
                new ChannelExternalPrincipal(senderId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelRichContent("", "", List.of(action)), List.of(), receivedAt, messageId);
        return Optional.of(new InboundFrame(reqId, envelope, text(body.get("response_url")), taskId));
    }

    Ack ack(String raw) {
        JSONObject root = parse(raw);
        JSONObject headers = object(root.get("headers"));
        return new Ack(text(headers.get("req_id")), integer(root.get("errcode"), Integer.MIN_VALUE), text(root.get("errmsg")));
    }

    private String messageContent(JSONObject body) {
        String type = text(body.get("msgtype"));
        if ("text".equalsIgnoreCase(type)) return text(object(body.get("text")).get("content"));
        if ("voice".equalsIgnoreCase(type)) return text(object(body.get("voice")).get("content"));
        return "";
    }

    private String frame(String cmd, String reqId, Map<String, Object> body) {
        return JSON.toJSONString(Map.of(
                "cmd", required(cmd, "WECOM_COMMAND_REQUIRED"),
                "headers", Map.of("req_id", required(reqId, "WECOM_REQUEST_ID_REQUIRED")),
                "body", body == null ? Map.of() : body));
    }

    private JSONObject parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("WECOM_FRAME_REQUIRED");
        JSONObject value = JSON.parseObject(raw);
        if (value == null) throw new IllegalArgumentException("WECOM_FRAME_INVALID");
        return value;
    }

    private JSONObject object(Object value) {
        if (value instanceof JSONObject object) return object;
        if (value instanceof Map<?, ?> map) return new JSONObject((Map) map);
        return new JSONObject();
    }

    private Instant instant(Object value) {
        long epoch = longValue(value, 0L);
        return epoch > 0 ? Instant.ofEpochSecond(epoch) : Instant.now();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record InboundFrame(String requestId,
                        ChannelInboundEnvelope envelope,
                        String responseUrl,
                        String taskId) {
        InboundFrame {
            requestId = requestId == null ? "" : requestId.trim();
            if (envelope == null) throw new IllegalArgumentException("WECOM_INBOUND_ENVELOPE_REQUIRED");
            responseUrl = responseUrl == null ? "" : responseUrl.trim();
            taskId = taskId == null ? "" : taskId.trim();
        }
    }

    record Ack(String requestId, int errorCode, String errorMessage) {
        boolean success() { return errorCode == 0; }
    }
}
