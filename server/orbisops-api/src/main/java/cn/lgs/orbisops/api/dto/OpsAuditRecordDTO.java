package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 运维 Agent 分析审计记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAuditRecordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String analysisId;
    private Boolean success;
    private String question;
    private String intent;
    private Integer rangeMinutes;
    private String promWindow;
    private String generatedAt;
    private Long durationMs;
    private List<String> selectedSources;
    private List<String> executedSources;
    private List<String> skippedSources;
    private Map<String, String> resultStatuses;
    private List<String> insightLevels;
    private String conclusion;
    private String errorMessage;

}
