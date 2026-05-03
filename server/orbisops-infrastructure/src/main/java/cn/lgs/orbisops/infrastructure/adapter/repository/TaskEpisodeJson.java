package cn.lgs.orbisops.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class TaskEpisodeJson {
    private TaskEpisodeJson() { }
    static String json(Object value) { return JSON.toJSONString(value); }
    static Map<String, Object> object(Object value) {
        if (value == null || value.toString().isBlank()) return new LinkedHashMap<>();
        return JSON.parseObject(value.toString(), new TypeReference<LinkedHashMap<String, Object>>() { });
    }
    static String text(Object value) { return value == null ? "" : value.toString(); }
    static long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(text(value)); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
