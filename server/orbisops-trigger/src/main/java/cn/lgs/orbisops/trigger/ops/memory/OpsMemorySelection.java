package cn.lgs.orbisops.trigger.ops.memory;

import java.util.List;
import java.util.Map;

public record OpsMemorySelection(String context, List<Map<String, Object>> refs) {
    public OpsMemorySelection {
        context = context == null ? "" : context;
        refs = refs == null ? List.of() : List.copyOf(refs);
    }
}
