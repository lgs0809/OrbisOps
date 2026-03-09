package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * Persists runtime snapshots without Fastjson reference markers. Agent state
 * deliberately reuses immutable lists and maps, while persisted JSON must stay
 * self-contained and independently readable.
 */
public final class OpsJsonSnapshotCodec {

    private OpsJsonSnapshotCodec() {
    }

    public static String write(Object value) {
        return value == null
                ? null
                : JSON.toJSONString(value, SerializerFeature.DisableCircularReferenceDetect);
    }

    public static <T> T read(String value, Class<T> type) {
        return value == null ? null : JSON.parseObject(value, type);
    }
}
