package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.Map;

/** Application-owned persisted payload contract for outbound Channel messages. */
public final class ChannelOutboundPayloadEncoder {

    public String pending(String contentHash, String contentPreview, Map<String, Object> metadata) {
        return CanonicalJson.stringifyPreservingOrder(Map.of(
                "contentHash", value(contentHash),
                "contentPreview", value(contentPreview),
                "metadata", metadata == null ? Map.of() : metadata));
    }

    public String delivery(Map<String, Object> delivery) {
        return CanonicalJson.stringifyPreservingOrder(delivery == null ? Map.of() : delivery);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
