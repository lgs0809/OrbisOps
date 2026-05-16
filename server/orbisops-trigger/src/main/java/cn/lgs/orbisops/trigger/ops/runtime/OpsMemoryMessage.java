package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsMemoryMessage {

    private String sessionId;
    private String userId;
    private String role;
    private String content;
    private String createdAt;
    private Map<String, Object> metadata;

    public Map<String, Object> safeMetadata() {
        return metadata == null ? Map.of() : metadata;
    }

    public String dedupKey() {
        return value(role) + ":" + value(content);
    }

    public static OpsMemoryMessage systemSummary(String sessionId, String userId, String content, String createdAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memory_type", "summary");
        metadata.put("source", "ops_context_compressor");
        return OpsMemoryMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role("system")
                .content(content)
                .createdAt(createdAt)
                .metadata(metadata)
                .build();
    }

    private static String value(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

}
