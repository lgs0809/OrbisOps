package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsRuntimeEvent {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String eventType;
    private String nodeId;
    private String nodeType;
    private String agent;
    private String source;
    private String status;
    private String summary;
    private String content;

    @Builder.Default
    private String timestamp = LocalDateTime.now().format(FORMATTER);

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static OpsRuntimeEvent of(String eventType, String status, String summary) {
        return OpsRuntimeEvent.builder()
                .eventType(eventType)
                .status(status)
                .summary(summary)
                .build();
    }

}
