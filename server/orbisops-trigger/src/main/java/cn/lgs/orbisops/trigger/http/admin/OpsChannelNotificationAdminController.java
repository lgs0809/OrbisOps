package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/channel-notifications")
public class OpsChannelNotificationAdminController {

    private final OpsChannelNotificationService notificationService;

    public OpsChannelNotificationAdminController(OpsChannelNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/outbox")
    public Response<List<Map<String, Object>>> list(@RequestParam("projectId") String projectId,
                                                    @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return response(notificationService.listOutbox(projectId, limit));
    }

    @PostMapping("/outbox/process")
    public Response<Map<String, Object>> process(@RequestParam(value = "limit", defaultValue = "20") int limit,
                                                 HttpServletRequest request) {
        actor(request);
        return response(notificationService.processPending(limit));
    }

    @PostMapping("/outbox/{id}/requeue")
    public Response<Map<String, Object>> requeue(@PathVariable("id") long id,
                                                 @RequestParam("projectId") String projectId,
                                                 HttpServletRequest request) {
        return response(notificationService.requeueDeadLetter(projectId, id, actor(request)));
    }

    @PostMapping("/outbox/{id}/cancel")
    public Response<Map<String, Object>> cancel(@PathVariable("id") long id,
                                                @RequestParam("projectId") String projectId,
                                                HttpServletRequest request) {
        return response(notificationService.cancelDeadLetter(projectId, id, actor(request)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.userId() == null || principal.userId().isBlank() ? principal.username() : principal.userId();
        }
        throw new SecurityException("未获取到已认证管理员");
    }

    private <T> Response<T> response(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
}
