package cn.lgs.orbisops.trigger.ops.channel.feishu;

import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsFeishuApprovalCardCodecTest {

    private final OpsFeishuApprovalCardCodec codec = new OpsFeishuApprovalCardCodec();

    @Test
    void card20UsesCallbackActionKeyWithoutEmbeddingChangePackageAuthorityFields() {
        String approveKey = "opaque-action-a";
        String rejectKey = "opaque-action-b";
        Map<String, Object> card = codec.approvalCard(new ChannelRichContent(
                "Review governed change",
                "Review governed change",
                List.of(
                        new ChannelInteractiveAction("approve-id", "Approve", approveKey,
                                ChannelInteractiveAction.ActionStyle.PRIMARY),
                        new ChannelInteractiveAction("reject-id", "Reject", rejectKey,
                                ChannelInteractiveAction.ActionStyle.DANGER))));

        assertEquals("2.0", card.get("schema"));
        Map<?, ?> body = (Map<?, ?>) card.get("body");
        List<?> elements = (List<?>) body.get("elements");
        Map<?, ?> columns = (Map<?, ?>) elements.get(1);
        List<?> columnList = (List<?>) columns.get("columns");
        Map<?, ?> firstColumn = (Map<?, ?>) columnList.get(0);
        List<?> firstElements = (List<?>) firstColumn.get("elements");
        Map<?, ?> firstButton = (Map<?, ?>) firstElements.get(0);
        List<?> behaviors = (List<?>) firstButton.get("behaviors");
        Map<?, ?> callback = (Map<?, ?>) behaviors.get(0);
        Map<?, ?> value = (Map<?, ?>) callback.get("value");
        assertEquals("callback", callback.get("type"));
        assertEquals(approveKey, value.get("actionKey"));

        String raw = CanonicalJson.stringify(card);
        assertFalse(raw.contains("packageId"));
        assertFalse(raw.contains("packageVersion"));
        assertFalse(raw.contains("packageHash"));
        assertFalse(raw.contains("approve-id"));
        assertFalse(raw.contains("reject-id"));
    }

    @Test
    void resolvedCardRemovesAllActions() {
        Map<String, Object> card = codec.resolvedCard("Approved in OrbisOps");

        String raw = CanonicalJson.stringify(card);
        assertEquals("2.0", card.get("schema"));
        assertFalse(raw.contains("behaviors"));
        assertFalse(raw.contains("actionKey"));
    }
}
