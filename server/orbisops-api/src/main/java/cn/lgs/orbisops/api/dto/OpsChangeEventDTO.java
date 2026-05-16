package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsChangeEventDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String eventId;
    private String packageId;
    private String eventType;
    private String actor;
    private String summary;
    private String previousHash;
    private String eventHash;
    private String createdAt;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
}
