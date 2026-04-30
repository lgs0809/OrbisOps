package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure application JSON helper for Skill Evolution pipeline payloads. */
public final class SkillEvolutionPayloadCodec {

    public String encode(Object value) {
        return CanonicalJson.stringifyPreservingOrder(value == null ? Map.of() : value);
    }

    public Map<String, Object> decodeObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new LinkedHashMap<>(CanonicalJson.parseObject(json));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
