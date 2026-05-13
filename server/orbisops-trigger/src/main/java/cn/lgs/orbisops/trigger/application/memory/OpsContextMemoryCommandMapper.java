package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryMutationCommand;

import java.math.BigDecimal;
import java.util.Map;

/** Trigger mapper from historical Map requests to typed Context Memory mutation commands. */
public class OpsContextMemoryCommandMapper {

    private final OpsContextMemoryMapper contextMemoryMapper = new OpsContextMemoryMapper();

    public ContextMemoryMutationCommand command(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ContextMemoryMutationCommand(
                string(safe, "memoryId"),
                string(safe, "scopeType"),
                string(safe, "scopeId"),
                string(safe, "memoryType"),
                string(safe, "title"),
                string(safe, "summary"),
                string(safe, "content"),
                safe.containsKey("keywords") ? contextMemoryMapper.keywords(safe.get("keywords")) : null,
                string(safe, "status"),
                safe.containsKey("confidence"),
                decimal(safe),
                string(safe, "sourceType"),
                string(safe, "sourceId"),
                string(safe, "sourceMessageHash"),
                string(safe, "createdBy"));
    }

    private String string(Map<String, Object> request, String key) {
        if (!request.containsKey(key)) return null;
        Object value = request.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal decimal(Map<String, Object> request) {
        if (!request.containsKey("confidence")) return null;
        Object value = request.get("confidence");
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value == null || String.valueOf(value).trim().isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
