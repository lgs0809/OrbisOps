package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Provider 模型目录同步结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientModelSyncResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String apiId;
    private String endpoint;
    private Integer httpStatus;
    private Integer fetchedCount;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private List<String> modelIds;
    private String errorMessage;
    private LocalDateTime syncedAt;
}
