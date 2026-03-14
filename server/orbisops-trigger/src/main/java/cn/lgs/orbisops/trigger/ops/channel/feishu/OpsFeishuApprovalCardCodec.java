package cn.lgs.orbisops.trigger.ops.channel.feishu;

import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;

import java.util.List;
import java.util.Map;

/** Feishu Card 2.0 rendering kept provider-local and free of ChangePackage authority fields. */
final class OpsFeishuApprovalCardCodec {

    Map<String, Object> approvalCard(ChannelRichContent content) {
        if (content == null || content.actions().isEmpty()) {
            throw new IllegalArgumentException("FEISHU_APPROVAL_CARD_ACTION_REQUIRED");
        }
        String summary = content.markdown().isBlank() ? content.plainText() : content.markdown();
        List<Map<String, Object>> columns = content.actions().stream().map(this::column).toList();
        return Map.of(
                "schema", "2.0",
                "header", Map.of("title", Map.of("tag", "plain_text", "content", "OrbisOps Approval Required")),
                "body", Map.of("elements", List.of(
                        Map.of("tag", "markdown", "content", summary),
                        Map.of("tag", "column_set", "flex_mode", "none", "columns", columns))));
    }

    Map<String, Object> resolvedCard(String status) {
        String safeStatus = status == null || status.isBlank() ? "Action resolved in OrbisOps" : status.trim();
        return Map.of(
                "schema", "2.0",
                "header", Map.of("title", Map.of("tag", "plain_text", "content", "OrbisOps Approval")),
                "body", Map.of("elements", List.of(Map.of("tag", "markdown", "content", safeStatus))));
    }

    private Map<String, Object> column(ChannelInteractiveAction action) {
        if (action == null) throw new IllegalArgumentException("FEISHU_APPROVAL_CARD_ACTION_REQUIRED");
        return Map.of(
                "tag", "column",
                "width", "weighted",
                "weight", 1,
                "elements", List.of(Map.of(
                        "tag", "button",
                        "text", Map.of("tag", "plain_text", "content", action.label()),
                        "type", action.style() == ChannelInteractiveAction.ActionStyle.DANGER ? "danger" : "primary",
                        "behaviors", List.of(Map.of(
                                "type", "callback",
                                "value", Map.of("actionKey", action.opaqueActionToken()))))));
    }
}
