package cn.lgs.orbisops.application.modelpolicy;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable persistence snapshot for one default-model policy. */
public record ModelDefaultPolicySnapshot(
        long id,
        ModelDefaultPolicy policy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public ModelDefaultPolicySnapshot {
        id = Math.max(0L, id);
        if (policy == null) throw new IllegalArgumentException("MODEL_DEFAULT_POLICY_REQUIRED");
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>(policy.toMap());
        result.put("id", id);
        result.put("createTime", createTime == null ? "" : createTime.toString());
        result.put("updateTime", updateTime == null ? "" : updateTime.toString());
        return Map.copyOf(result);
    }
}
