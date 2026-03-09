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
public class OpsEvidenceReferenceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String evidenceId;
    private String sourceType;
    private String sourceId;
    private String summary;
    private String observedAt;
    private String contentHash;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
