package cn.lgs.orbisops.trigger.ops.channel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable JSON representation boundary for Channel outbox metadata. */
public final class OpsChannelOutboxMetadataCodec {

    public String encode(Map<String, Object> metadata) {
        return JSON.toJSONString(
                metadata == null ? Map.of() : metadata,
                SerializerFeature.WriteMapNullValue);
    }

    public Map<String, Object> decode(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return Collections.unmodifiableMap(
                    new LinkedHashMap<>(JSON.parseObject(metadataJson)));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
