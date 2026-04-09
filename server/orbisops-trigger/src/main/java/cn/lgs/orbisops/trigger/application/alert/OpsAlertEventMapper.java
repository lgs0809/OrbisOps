package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerEvent;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpsAlertEventMapper {

    public AlertEventDraft draft(
            OpsAlertTriggerRule rule,
            String sourceType,
            String status,
            String dispatchKey,
            String fingerprint,
            String alertName,
            String severity,
            String serviceName,
            String runId,
            String runStatus,
            String finalSummary,
            String errorMessage,
            Map<String, Object> labels,
            Map<String, Object> annotations,
            Map<String, Object> payload) {
        if (rule == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        return new AlertEventDraft(
                rule.getId(),
                rule.getRuleName(),
                rule.getProjectId(),
                sourceType,
                status,
                dispatchKey,
                fingerprint,
                alertName,
                severity,
                serviceName,
                rule.getNotificationTarget(),
                runId,
                runStatus,
                finalSummary,
                errorMessage,
                labels,
                annotations,
                payload);
    }

    public OpsAlertTriggerEvent view(AlertEventSnapshot event) {
        if (event == null) return null;
        return OpsAlertTriggerEvent.builder()
                .id(event.id())
                .ruleId(event.ruleId())
                .ruleName(event.ruleName())
                .projectId(event.projectId())
                .sourceType(event.sourceType())
                .status(event.status())
                .dedupKey(event.dispatchKey())
                .fingerprint(event.fingerprint())
                .alertName(event.alertName())
                .severity(event.severity())
                .serviceName(event.serviceName())
                .receiver(event.receiver())
                .runId(event.runId())
                .runStatus(event.runStatus())
                .finalSummary(event.finalSummary())
                .completedAt(event.completedAt())
                .errorMessage(event.errorMessage())
                .labelsJson(event.labels().isEmpty() ? "" : JSON.toJSONString(event.labels()))
                .annotationsJson(event.annotations().isEmpty() ? "" : JSON.toJSONString(event.annotations()))
                .payloadJson(event.payload().isEmpty() ? "" : JSON.toJSONString(event.payload()))
                .createTime(event.createTime())
                .build();
    }

    public List<OpsAlertTriggerEvent> views(List<AlertEventSnapshot> events) {
        return events == null ? List.of() : events.stream().map(this::view).toList();
    }
}
