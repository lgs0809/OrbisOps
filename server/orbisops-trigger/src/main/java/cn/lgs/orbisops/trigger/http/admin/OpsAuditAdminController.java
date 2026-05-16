package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsAuditRecordDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.audit.AnalysisAuditApplicationService;
import cn.lgs.orbisops.trigger.application.audit.OpsAnalysisAuditMapper;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** 运维 Agent 运行审计 HTTP Adapter。 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsAuditAdminController {

    private final AnalysisAuditApplicationService audits;
    private final OpsAnalysisAuditMapper mapper;

    public OpsAuditAdminController(
            AnalysisAuditApplicationService audits,
            OpsAnalysisAuditMapper mapper) {
        this.audits = audits;
        this.mapper = mapper;
    }

    @GetMapping("/audits")
    public Response<List<OpsAuditRecordDTO>> listAuditRecords(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return Response.<List<OpsAuditRecordDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.views(audits.list(Optional.ofNullable(limit).orElse(20))))
                .build();
    }
}
