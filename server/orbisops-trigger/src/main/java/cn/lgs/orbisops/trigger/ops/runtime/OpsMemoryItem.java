package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsMemoryItem {

    private String sessionId;
    private String userId;
    private String memoryType;
    private String content;
    private BigDecimal importance;
    private String tagsJson;
    private String sourceMessageRole;
    private String sourceMessageHash;
    private Map<String, Object> metadata;
    private String createdAt;

    public String dedupKey() {
        return (memoryType == null ? "" : memoryType) + ":" + (content == null ? "" : content);
    }

}
