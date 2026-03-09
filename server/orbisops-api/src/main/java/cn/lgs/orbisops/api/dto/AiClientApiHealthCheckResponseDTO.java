package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 客户端 API Provider 健康检测结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApiHealthCheckResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String apiId;
    private String testType;
    private String endpoint;
    private String status;
    private Integer httpStatus;
    private Long latencyMs;
    private String errorMessage;
    private String testedBy;
    private LocalDateTime testTime;
    private LocalDateTime checkedAt;
}
