package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.application.alert.AlertTriggerProcessManager;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerEvent;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookResult;
import cn.lgs.orbisops.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 运维告警触发器 HTTP Adapter。 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsAlertTriggerAdminController {

    private final AlertRuleManagementApplicationService alertRules;
    private final AlertEventApplicationService alertEvents;
    private final AlertTriggerProcessManager<OpsAlertWebhookResult> alertProcess;
    private final OpsAlertRuleMapper ruleMapper;
    private final OpsAlertEventMapper eventMapper;

    public OpsAlertTriggerAdminController(
            AlertRuleManagementApplicationService alertRules,
            AlertEventApplicationService alertEvents,
            AlertTriggerProcessManager<OpsAlertWebhookResult> alertProcess,
            OpsAlertRuleMapper ruleMapper,
            OpsAlertEventMapper eventMapper) {
        this.alertRules = alertRules;
        this.alertEvents = alertEvents;
        this.alertProcess = alertProcess;
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
    }

    @GetMapping("/alert-triggers/rules")
    public Response<List<OpsAlertTriggerRule>> listAlertTriggerRules() {
        return success(ruleMapper.views(alertRules.list()).stream().map(this::maskedCopy).toList());
    }

    @PostMapping("/alert-triggers/rules")
    public Response<OpsAlertTriggerRule> createAlertTriggerRule(
            @RequestBody OpsAlertTriggerRule rule,
            HttpServletRequest servletRequest) {
        return success(maskedCopy(ruleMapper.view(
                alertRules.save(ruleMapper.candidate(rule), actor(servletRequest)))));
    }

    @PutMapping("/alert-triggers/rules")
    public Response<OpsAlertTriggerRule> updateAlertTriggerRule(
            @RequestBody OpsAlertTriggerRule rule,
            HttpServletRequest servletRequest) {
        return success(maskedCopy(ruleMapper.view(
                alertRules.save(ruleMapper.candidate(rule), actor(servletRequest)))));
    }

    @PutMapping("/alert-triggers/rules/{id}/status")
    public Response<Boolean> updateAlertTriggerRuleStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") Integer status,
            HttpServletRequest servletRequest) {
        return success(alertRules.updateStatus(id, status, actor(servletRequest)));
    }

    @DeleteMapping("/alert-triggers/rules/{id}")
    public Response<Boolean> deleteAlertTriggerRule(
            @PathVariable("id") Long id,
            HttpServletRequest servletRequest) {
        return success(alertRules.delete(id, actor(servletRequest)));
    }

    @GetMapping("/alert-triggers/events")
    public Response<List<OpsAlertTriggerEvent>> listAlertTriggerEvents(
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        return success(eventMapper.views(alertEvents.list(Optional.ofNullable(limit).orElse(50))));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/alert-triggers/webhook/alertmanager")
    public Response<OpsAlertWebhookResult> receiveAlertmanagerWebhook(
            @RequestBody(required = false) String payloadJson,
            @RequestHeader(value = "X-Ops-Alert-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Ops-Alert-Signature", required = false) String signature) {
        Map<String, Object> payload = StringUtils.hasText(payloadJson)
                ? Optional.ofNullable((Map<String, Object>) JSON.parseObject(payloadJson, Map.class)).orElse(Map.of())
                : Map.of();
        return success(alertProcess.receiveAlertmanager(payloadJson, payload, timestamp, signature));
    }

    @PostMapping("/alert-triggers/outbox/process")
    public Response<AlertTriggerOutboxOutcome> processAlertTriggerOutbox(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return success(alertProcess.processOutbox(Optional.ofNullable(limit).orElse(20)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private OpsAlertTriggerRule maskedCopy(OpsAlertTriggerRule rule) {
        if (rule == null) return null;
        OpsAlertTriggerRule result = JSON.parseObject(JSON.toJSONString(rule), OpsAlertTriggerRule.class);
        if (StringUtils.hasText(result.getWebhookSecret())) result.setWebhookSecret("******");
        return result;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
