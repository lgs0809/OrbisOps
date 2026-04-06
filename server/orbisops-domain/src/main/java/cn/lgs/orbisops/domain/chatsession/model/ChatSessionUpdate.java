package cn.lgs.orbisops.domain.chatsession.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** CAS update request for mutable Chat Session display fields. */
public record ChatSessionUpdate(
        String sessionId,
        String title,
        String status,
        Map<String, Object> metadata,
        long expectedStateVersion) {

    public ChatSessionUpdate {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
