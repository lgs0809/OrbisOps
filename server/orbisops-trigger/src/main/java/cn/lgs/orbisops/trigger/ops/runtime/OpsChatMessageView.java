package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsChatMessageView {

    private String messageId;
    private String sessionId;
    private String userId;
    private String role;
    private String content;
    private String createdAt;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
