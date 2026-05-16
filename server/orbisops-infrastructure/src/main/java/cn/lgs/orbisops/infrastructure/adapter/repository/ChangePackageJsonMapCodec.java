package cn.lgs.orbisops.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/** Infrastructure codec for persisted ChangePackage JSON object columns. */
final class ChangePackageJsonMapCodec {

    private ChangePackageJsonMapCodec() {
    }

    static String encode(Map<String, Object> value) {
        return JSON.toJSONString(value == null ? Map.of() : value);
    }

    static Map<String, Object> decode(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(json);
            return parsed == null || parsed.isEmpty() ? Map.of() : new LinkedHashMap<>(parsed);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_JSON_OBJECT_INVALID", failure);
        }
    }
}
