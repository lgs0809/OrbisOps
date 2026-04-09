package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.service.IncidentPolicy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class IncidentCommandApplicationService {

    private static final Set<String> USER_TIMELINE_EVENTS = Set.of(
            "NOTE", "USER_NOTE", "COMMENT", "USER_FEEDBACK");

    private final IIncidentRepository incidents;
    private final IncidentAuditPort audit;
    private final Supplier<String> incidentIdSupplier;
    private final IncidentTransactionPort transactions;
    private final IncidentPolicy policy;

    public IncidentCommandApplicationService(
            IIncidentRepository incidents,
            IncidentAuditPort audit,
            Supplier<String> incidentIdSupplier,
            IncidentTransactionPort transactions) {
        if (incidents == null) throw new IllegalArgumentException("INCIDENT_REPOSITORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("INCIDENT_AUDIT_PORT_REQUIRED");
        if (incidentIdSupplier == null) throw new IllegalArgumentException("INCIDENT_ID_SUPPLIER_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("INCIDENT_TRANSACTION_PORT_REQUIRED");
        this.incidents = incidents;
        this.audit = audit;
        this.incidentIdSupplier = incidentIdSupplier;
        this.transactions = transactions;
        this.policy = new IncidentPolicy();
    }

    public IncidentSnapshot create(CreateIncidentCommand command, String actor) {
        if (command == null) throw new IllegalArgumentException("INCIDENT_CREATE_COMMAND_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentDraft draft = policy.manual(
                incidentIdSupplier.get(),
                command.projectId(),
                command.title(),
                command.status(),
                command.severity(),
                command.serviceName(),
                command.sourceType(),
                command.summary(),
                command.labels(),
                command.metadata(),
                command.affectedResources());
        return transactions.required(() -> {
            IncidentSnapshot saved = incidents.create(draft);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("projectId", draft.projectId());
            details.put("title", draft.title());
            details.put("actor", operator);
            String sourceType = value(draft.sourceType()).isBlank() ? "MANUAL" : draft.sourceType();
            incidents.appendTimeline(policy.timeline(
                    draft.incidentId(),
                    "INCIDENT_CREATED",
                    "创建故障事件",
                    draft.summary(),
                    operator,
                    sourceType,
                    draft.incidentId(),
                    details));
            IncidentSnapshot result = incidents.find(draft.incidentId()).orElse(saved);
            audit.record(new IncidentAuditEvent(
                    "create", draft.incidentId(), operator, null, result, details));
            return result;
        });
    }

    /**
     * Opens or reuses one deterministic Incident for a recurring producer such as Schedule.
     * The recurring key is product identity, not a runtime state machine.
     */
    public IncidentSnapshot openRecurring(
            String recurringKey,
            CreateIncidentCommand command,
            String actor) {
        if (command == null) throw new IllegalArgumentException("INCIDENT_CREATE_COMMAND_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        String projectId = required(command.projectId(), "INCIDENT_PROJECT_ID_REQUIRED");
        String key = required(recurringKey, "INCIDENT_RECURRING_KEY_REQUIRED");
        String incidentId = "incident_" + UUID.nameUUIDFromBytes(
                        (projectId + ":" + key).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return transactions.required(() -> {
            var existing = incidents.find(incidentId);
            if (existing.isPresent()) {
                IncidentSnapshot current = existing.get();
                if (current.status() == IncidentStatus.CLOSED || current.status() == IncidentStatus.RESOLVED) {
                    incidents.updateStatus(incidentId, IncidentStatus.OPEN);
                    incidents.appendTimeline(policy.timeline(
                            incidentId,
                            "INCIDENT_REOPENED",
                            "重复异常重新打开事件",
                            "同一 recurring condition 再次出现，开始新的 Incident occurrence。",
                            operator,
                            value(command.sourceType()).isBlank() ? "RECURRING" : command.sourceType(),
                            key,
                            Map.of("recurringKey", key)));
                    IncidentSnapshot reopened = incidents.find(incidentId).orElse(current);
                    audit.record(new IncidentAuditEvent(
                            "reopen-recurring", incidentId, operator, current, reopened,
                            Map.of("recurringKey", key)));
                    return reopened;
                }
                return current;
            }
            IncidentDraft draft = policy.manual(
                    incidentId,
                    projectId,
                    command.title(),
                    command.status(),
                    command.severity(),
                    command.serviceName(),
                    command.sourceType(),
                    command.summary(),
                    command.labels(),
                    command.metadata(),
                    command.affectedResources());
            IncidentSnapshot saved = incidents.create(draft);
            incidents.appendTimeline(policy.timeline(
                    incidentId,
                    "INCIDENT_CREATED",
                    "创建 recurring 故障事件",
                    draft.summary(),
                    operator,
                    value(draft.sourceType()).isBlank() ? "RECURRING" : draft.sourceType(),
                    key,
                    Map.of("recurringKey", key)));
            IncidentSnapshot result = incidents.find(incidentId).orElse(saved);
            audit.record(new IncidentAuditEvent(
                    "create-recurring", incidentId, operator, null, result,
                    Map.of("recurringKey", key)));
            return result;
        });
    }

    public IncidentSnapshot updateStatus(String incidentId, String status, String actor, String note) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot before = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        IncidentStatus target = IncidentStatus.require(status);
        if (target != IncidentStatus.OPEN && target != IncidentStatus.CLOSED) {
            throw new IllegalArgumentException("INCIDENT_STATUS_IS_FACT_PROJECTED:" + target.name());
        }
        String detail = value(note);
        return transactions.required(() -> {
            IncidentSnapshot updated = incidents.updateStatus(id, target);
            Map<String, Object> details = Map.of("status", target.name(), "actor", operator);
            incidents.appendTimeline(policy.timeline(
                    id,
                    target == IncidentStatus.CLOSED ? "INCIDENT_CLOSED" : "INCIDENT_REOPENED",
                    target == IncidentStatus.CLOSED ? "事件已关闭" : "事件已重新打开",
                    detail,
                    operator,
                    "INCIDENT",
                    id,
                    details));
            IncidentSnapshot result = incidents.find(id).orElse(updated);
            audit.record(new IncidentAuditEvent(
                    "status", id, operator, before, result, details));
            return result;
        });
    }

    public IncidentSnapshot assignOwner(String incidentId, String ownerUserId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        String owner = value(ownerUserId);
        IncidentSnapshot before = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        return transactions.required(() -> {
            IncidentSnapshot updated = incidents.assignOwner(id, owner);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("actor", operator);
            details.put("previousOwnerUserId", value(before.ownerUserId()));
            details.put("ownerUserId", owner);
            incidents.appendTimeline(policy.timeline(
                    id,
                    owner.isBlank() ? "INCIDENT_OWNER_CLEARED" : "INCIDENT_OWNER_ASSIGNED",
                    owner.isBlank() ? "事件责任人已清空" : "事件责任人已更新",
                    owner.isBlank() ? "当前 Incident 暂无明确责任人。" : "当前责任人：" + owner,
                    operator,
                    "USER",
                    owner.isBlank() ? id : owner,
                    details));
            IncidentSnapshot result = incidents.find(id).orElse(updated);
            audit.record(new IncidentAuditEvent(
                    "assign-owner", id, operator, before, result, details));
            return result;
        });
    }

    public void addWatcher(String incidentId, String watcherUserId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String watcher = required(watcherUserId, "INCIDENT_WATCHER_USER_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        boolean exists = incidents.watchers(id).stream().anyMatch(item -> watcher.equalsIgnoreCase(item.userId()));
        if (exists) return;
        transactions.required(() -> {
            incidents.addWatcher(id, watcher, operator);
            incidents.appendTimeline(policy.timeline(
                    id,
                    "INCIDENT_WATCHER_ADDED",
                    "新增事件关注人",
                    watcher + " 开始关注该 Incident。",
                    operator,
                    "USER",
                    watcher,
                    Map.of("watcherUserId", watcher, "actor", operator)));
            audit.record(new IncidentAuditEvent(
                    "watcher-add", id, operator, incident, incident, Map.of("watcherUserId", watcher)));
            return Boolean.TRUE;
        });
    }

    public void removeWatcher(String incidentId, String watcherUserId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String watcher = required(watcherUserId, "INCIDENT_WATCHER_USER_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        boolean exists = incidents.watchers(id).stream().anyMatch(item -> watcher.equalsIgnoreCase(item.userId()));
        if (!exists) return;
        transactions.required(() -> {
            incidents.removeWatcher(id, watcher);
            incidents.appendTimeline(policy.timeline(
                    id,
                    "INCIDENT_WATCHER_REMOVED",
                    "取消事件关注",
                    watcher + " 不再关注该 Incident。",
                    operator,
                    "USER",
                    watcher,
                    Map.of("watcherUserId", watcher, "actor", operator)));
            audit.record(new IncidentAuditEvent(
                    "watcher-remove", id, operator, incident, incident, Map.of("watcherUserId", watcher)));
            return Boolean.TRUE;
        });
    }

    public void relate(String incidentId, String relatedIncidentId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String relatedId = required(relatedIncidentId, "INCIDENT_RELATED_ID_REQUIRED");
        if (id.equals(relatedId)) throw new IllegalArgumentException("INCIDENT_CANNOT_RELATE_SELF");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        IncidentSnapshot related = incidents.find(relatedId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + relatedId));
        if (!incident.projectId().equals(related.projectId())) {
            throw new SecurityException("INCIDENT_RELATION_PROJECT_MISMATCH");
        }
        boolean exists = incidents.relations(id).stream()
                .anyMatch(item -> relatedId.equals(item.relatedIncidentId()) && "RELATED".equalsIgnoreCase(item.relationType()));
        if (exists) return;
        transactions.required(() -> {
            incidents.addRelation(id, relatedId, "RELATED", operator);
            appendRelationTimeline(id, relatedId, related.title(), operator, "INCIDENT_RELATED", "关联相关 Incident");
            appendRelationTimeline(relatedId, id, incident.title(), operator, "INCIDENT_RELATED", "关联相关 Incident");
            audit.record(new IncidentAuditEvent(
                    "relation-add", id, operator, incident, incident, Map.of("relatedIncidentId", relatedId)));
            return Boolean.TRUE;
        });
    }

    public void unrelate(String incidentId, String relatedIncidentId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String relatedId = required(relatedIncidentId, "INCIDENT_RELATED_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        IncidentSnapshot related = incidents.find(relatedId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + relatedId));
        if (!incident.projectId().equals(related.projectId())) {
            throw new SecurityException("INCIDENT_RELATION_PROJECT_MISMATCH");
        }
        transactions.required(() -> {
            incidents.removeRelation(id, relatedId, "RELATED");
            appendRelationTimeline(id, relatedId, related.title(), operator, "INCIDENT_RELATION_REMOVED", "解除相关 Incident");
            appendRelationTimeline(relatedId, id, incident.title(), operator, "INCIDENT_RELATION_REMOVED", "解除相关 Incident");
            audit.record(new IncidentAuditEvent(
                    "relation-remove", id, operator, incident, incident, Map.of("relatedIncidentId", relatedId)));
            return Boolean.TRUE;
        });
    }

    public IncidentTimelineEntry addComment(String incidentId, String comment, String actor) {
        String detail = required(comment, "INCIDENT_COMMENT_REQUIRED");
        return appendUserTimeline(
                incidentId,
                new AppendIncidentTimelineCommand(
                        "COMMENT",
                        "团队评论",
                        detail,
                        "USER",
                        actor,
                        Map.of("comment", detail)),
                actor);
    }

    private void appendRelationTimeline(
            String incidentId,
            String relatedIncidentId,
            String relatedTitle,
            String actor,
            String eventType,
            String title) {
        incidents.appendTimeline(policy.timeline(
                incidentId,
                eventType,
                title,
                relatedTitle,
                actor,
                "INCIDENT",
                relatedIncidentId,
                Map.of("relatedIncidentId", relatedIncidentId)));
    }

    public IncidentTimelineEntry appendTimeline(
            String incidentId,
            AppendIncidentTimelineCommand command,
            String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        if (command == null) throw new IllegalArgumentException("INCIDENT_TIMELINE_COMMAND_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        Map<String, Object> payload = new LinkedHashMap<>(command.payload());
        payload.put("actor", operator);
        IncidentTimelineDraft draft = policy.timeline(
                id,
                text(command.eventType(), "NOTE"),
                text(command.title(), "人工记录"),
                value(command.detail()),
                operator,
                text(command.refType(), "MANUAL"),
                text(command.refId(), id),
                payload);
        return transactions.required(() -> {
            IncidentTimelineEntry entry = incidents.appendTimeline(draft)
                    .orElseThrow(() -> new IllegalStateException("INCIDENT_TIMELINE_STORE_UNAVAILABLE"));
            audit.record(new IncidentAuditEvent(
                    "timeline",
                    id,
                    operator,
                    incident,
                    incident,
                    Map.of("eventType", entry.eventType(), "refId", entry.refId())));
            return entry;
        });
    }

    /**
     * User-entered timeline data is limited to notes/feedback. State-driving facts are
     * owned by their application use cases and cannot be synthesized through a generic API.
     */
    public IncidentTimelineEntry appendUserTimeline(
            String incidentId,
            AppendIncidentTimelineCommand command,
            String actor) {
        if (command == null) throw new IllegalArgumentException("INCIDENT_TIMELINE_COMMAND_REQUIRED");
        String eventType = text(command.eventType(), "NOTE").toUpperCase(java.util.Locale.ROOT);
        if (!USER_TIMELINE_EVENTS.contains(eventType)) {
            throw new SecurityException("INCIDENT_USER_TIMELINE_EVENT_FORBIDDEN:" + eventType);
        }
        return appendTimeline(
                incidentId,
                new AppendIncidentTimelineCommand(
                        eventType,
                        command.title(),
                        command.detail(),
                        command.refType(),
                        command.refId(),
                        command.payload()),
                actor);
    }

    public IncidentTimelineEntry confirmHelpful(String incidentId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        if (!currentEpisodeVerified(id)) {
            throw new IllegalStateException("INCIDENT_HELPFUL_REQUIRES_VERIFIED_RESOLUTION");
        }
        return appendTimeline(
                id,
                new AppendIncidentTimelineCommand(
                        "USER_CONFIRMED_HELPFUL",
                        "用户确认本次处置有帮助",
                        "用于产品结果指标，只记录经过认证用户的明确确认，不由模型或自由事件类型推断。",
                        "INCIDENT",
                        id,
                        Map.of("feedbackType", "HELPFUL")),
                actor);
    }

    private boolean currentEpisodeVerified(String incidentId) {
        for (var item : incidents.timeline(incidentId, 300)) {
            if (item == null) continue;
            String eventType = value(item.eventType()).toUpperCase(java.util.Locale.ROOT);
            if ("INCIDENT_REOPENED".equals(eventType)) return false;
            if ("VERIFICATION_SUCCEEDED".equals(eventType)) return true;
            if ("VERIFICATION_FAILED".equals(eventType)
                    || "VERIFICATION_INSUFFICIENT".equals(eventType)) return false;
        }
        return false;
    }

    public void linkRun(String incidentId, String runId, String actor, String detail) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String run = required(runId, "INCIDENT_RUN_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot before = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));
        transactions.required(() -> {
            incidents.linkRun(id, run);
            incidents.appendTimeline(policy.timeline(
                    id,
                    "ANALYSIS_LINKED",
                    "关联运维分析",
                    text(detail, "人工关联运维分析 run"),
                    operator,
                    "RUN",
                    run,
                    Map.of("runId", run)));
            IncidentSnapshot after = incidents.find(id).orElse(before);
            audit.record(new IncidentAuditEvent(
                    "link-run", id, operator, before, after, Map.of("runId", run)));
            return Boolean.TRUE;
        });
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String text(String value, String fallback) {
        String normalized = value(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
