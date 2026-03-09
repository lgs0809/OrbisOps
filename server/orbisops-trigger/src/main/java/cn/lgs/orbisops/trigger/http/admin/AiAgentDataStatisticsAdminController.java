package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.DataStatisticsResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.statistics.DataStatisticsQueryApplicationService;
import cn.lgs.orbisops.trigger.application.statistics.OpsDataStatisticsMapper;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据统计接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/data/statistics")
public class AiAgentDataStatisticsAdminController {

    private final DataStatisticsQueryApplicationService queries;
    private final OpsDataStatisticsMapper mapper;

    public AiAgentDataStatisticsAdminController(
            DataStatisticsQueryApplicationService queries,
            OpsDataStatisticsMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    @GetMapping("/get-data-statistics")
    public Response<DataStatisticsResponseDTO> getDataStatistics() {
        try {
            return Response.<DataStatisticsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(mapper.response(queries.snapshot()))
                    .build();
        } catch (Exception exception) {
            log.error("获取系统数据统计失败", exception);
            return Response.<DataStatisticsResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }
}
