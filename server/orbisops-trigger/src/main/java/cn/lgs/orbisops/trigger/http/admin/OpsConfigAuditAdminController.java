package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 运维配置审计 HTTP Adapter。 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsConfigAuditAdminController {

    private final OpsConfigAuditService audits;

    public OpsConfigAuditAdminController(OpsConfigAuditService audits) {
        this.audits = audits;
    }

    @GetMapping("/config-audits")
    public Response<List<Map<String, Object>>> listConfigAudits(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(audits.search(query(projectId, userId, agentId, module, action, riskLevel,
                startTime, endTime, Optional.ofNullable(limit).orElse(100))));
    }

    @GetMapping("/config-audits/policy")
    public Response<Map<String, Object>> auditPolicy(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return success(audits.getPolicy(projectId));
    }

    @PutMapping("/config-audits/policy")
    public Response<Map<String, Object>> updateAuditPolicy(
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            return success(audits.updatePolicy(request == null ? Map.of() : request));
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    @GetMapping("/config-audits/export")
    public Response<Map<String, Object>> exportConfigAudits(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "500") Integer limit) {
        return success(audits.exportSearch(query(projectId, userId, agentId, module, action, riskLevel,
                startTime, endTime, Optional.ofNullable(limit).orElse(500))));
    }

    @GetMapping("/config-audits/{auditId}")
    public Response<Map<String, Object>> configAuditDetail(@PathVariable("auditId") String auditId) {
        return success(audits.detail(auditId));
    }

    @GetMapping("/config-audits/{auditId}/export")
    public Response<Map<String, Object>> exportConfigAudit(@PathVariable("auditId") String auditId) {
        return success(audits.exportOne(auditId));
    }

    private OpsConfigAuditService.AuditQuery query(
            String projectId,
            String userId,
            String agentId,
            String module,
            String action,
            String riskLevel,
            String startTime,
            String endTime,
            int limit) {
        return new OpsConfigAuditService.AuditQuery(
                projectId, userId, agentId, module, action, riskLevel, startTime, endTime, limit);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private Response<Map<String, Object>> failure(String message) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .data(null)
                .build();
    }
}
