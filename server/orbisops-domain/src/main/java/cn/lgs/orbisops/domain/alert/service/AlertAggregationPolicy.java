package cn.lgs.orbisops.domain.alert.service;

import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSeed;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateState;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationAction;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationSignal;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AlertAggregationPolicy {

    public AlertAggregationSignal signal(
            String projectId,
            long ruleId,
            String fingerprint,
            String alertStatus,
            String severity,
            String affectedResource,
            Map<String, Object> payload,
            int debounceSeconds,
            int maxWaitSeconds) {
        String normalizedSeverity = normalizeSeverity(severity);
        return new AlertAggregationSignal(
                projectId,
                ruleId,
                fingerprint,
                AlertAggregateState.fromAlertStatus(alertStatus),
                normalizedSeverity,
                severityRank(normalizedSeverity),
                affectedResource,
                payload,
                debounceSeconds,
                maxWaitSeconds);
    }

    public AlertAggregateSeed seed(AlertAggregationSignal signal) {
        if (signal == null) throw new IllegalArgumentException("ALERT_AGGREGATION_SIGNAL_REQUIRED");
        return new AlertAggregateSeed(
                aggregateKey(signal.projectId(), signal.ruleId(), signal.fingerprint()),
                signal.projectId(),
                signal.ruleId(),
                signal.fingerprint(),
                signal.state(),
                signal.severity(),
                signal.severityRank(),
                resourceList(signal.affectedResource()),
                signal.payload());
    }

    public AlertAggregationPlan plan(
            boolean inserted,
            AlertAggregateSnapshot current,
            AlertAggregationSignal signal) {
        if (current == null) throw new IllegalArgumentException("ALERT_AGGREGATE_SNAPSHOT_REQUIRED");
        if (signal == null) throw new IllegalArgumentException("ALERT_AGGREGATION_SIGNAL_REQUIRED");
        List<String> resources = mergeResources(current.affectedResources(), signal.affectedResource());
        if (inserted) {
            if (signal.state() == AlertAggregateState.RESOLVED) {
                return plan(current, AlertAggregationAction.NONE, AlertAggregateEventType.IGNORED_RECOVERY,
                        current.state(), current.severity(), current.severityRank(), resources, signal);
            }
            return plan(current, AlertAggregationAction.MARK_FIRST, AlertAggregateEventType.FIRST,
                    current.state(), current.severity(), current.severityRank(), resources, signal);
        }
        if (signal.state() == AlertAggregateState.RESOLVED) {
            if (current.state() == AlertAggregateState.RESOLVED) {
                return plan(current, AlertAggregationAction.TOUCH_RESOLVED,
                        AlertAggregateEventType.IGNORED_RECOVERY,
                        AlertAggregateState.RESOLVED, current.severity(), current.severityRank(), resources, signal);
            }
            return plan(current, AlertAggregationAction.MARK_IMMEDIATE, AlertAggregateEventType.RECOVERY,
                    AlertAggregateState.RESOLVED, signal.severity(), signal.severityRank(), resources, signal);
        }
        if (current.state() == AlertAggregateState.RESOLVED) {
            return plan(current, AlertAggregationAction.MARK_IMMEDIATE, AlertAggregateEventType.RECURRENCE,
                    AlertAggregateState.FIRING, signal.severity(), signal.severityRank(), resources, signal);
        }
        if (signal.severityRank() > current.severityRank()) {
            return plan(current, AlertAggregationAction.MARK_IMMEDIATE, AlertAggregateEventType.ESCALATION,
                    AlertAggregateState.FIRING, signal.severity(), signal.severityRank(), resources, signal);
        }
        return plan(current, AlertAggregationAction.SCHEDULE_SUMMARY, AlertAggregateEventType.DUPLICATE,
                AlertAggregateState.FIRING, signal.severity(),
                Math.max(current.severityRank(), signal.severityRank()), resources, signal);
    }

    public AlertAggregationDecision decision(
            AlertAggregateSnapshot current,
            AlertAggregateEventType eventType) {
        if (current == null) throw new IllegalArgumentException("ALERT_AGGREGATE_SNAPSHOT_REQUIRED");
        if (eventType == null) throw new IllegalArgumentException("ALERT_AGGREGATION_EVENT_TYPE_REQUIRED");
        return new AlertAggregationDecision(
                current.aggregateKey(),
                dispatchKey(current.aggregateKey(), eventType, current.version()),
                eventType,
                eventType.dispatchNow(),
                current.severityRank(),
                current.occurrenceCount(),
                current.pendingSummaryCount(),
                current.version(),
                current.affectedResources());
    }

    public AlertSummaryClaim claim(AlertAggregateSnapshot aggregate, String claimToken) {
        if (aggregate == null) throw new IllegalArgumentException("ALERT_AGGREGATE_SNAPSHOT_REQUIRED");
        long nextVersion = aggregate.version() + 1;
        return new AlertSummaryClaim(
                aggregate.aggregateKey(),
                dispatchKey(aggregate.aggregateKey(), AlertAggregateEventType.SUMMARY, nextVersion),
                aggregate.projectId(),
                aggregate.ruleId(),
                aggregate.fingerprint(),
                aggregate.severity(),
                aggregate.severityRank(),
                aggregate.occurrenceCount(),
                aggregate.pendingSummaryCount(),
                aggregate.payload(),
                aggregate.affectedResources(),
                nextVersion,
                claimToken);
    }

    public int claimLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    public int staleClaimSeconds(int seconds) {
        return Math.max(30, seconds);
    }

    public int debounceSeconds(int seconds) {
        return Math.max(1, seconds);
    }

    public int severityRank(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "CRITICAL", "FATAL", "P0" -> 100;
            case "HIGH", "ERROR", "P1" -> 80;
            case "MEDIUM", "WARN", "WARNING", "P2" -> 50;
            default -> 20;
        };
    }

    public String normalizeSeverity(String severity) {
        String normalized = severity == null ? "" : severity.trim();
        return normalized.isBlank() ? "WARNING" : normalized.toUpperCase(Locale.ROOT);
    }

    private AlertAggregationPlan plan(
            AlertAggregateSnapshot current,
            AlertAggregationAction action,
            AlertAggregateEventType eventType,
            AlertAggregateState targetState,
            String severity,
            int severityRank,
            List<String> resources,
            AlertAggregationSignal signal) {
        return new AlertAggregationPlan(
                current.aggregateKey(),
                action,
                eventType,
                targetState,
                severity,
                severityRank,
                resources,
                signal.payload(),
                signal.debounceSeconds(),
                signal.maxWaitSeconds(),
                current.version());
    }

    private String aggregateKey(String projectId, long ruleId, String fingerprint) {
        return md5(projectId + ":" + ruleId + ":" + fingerprint);
    }

    private String dispatchKey(String aggregateKey, AlertAggregateEventType type, long version) {
        return md5(aggregateKey + ":" + type.name() + ":" + version);
    }

    private String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("ALERT_AGGREGATION_HASH_UNAVAILABLE", e);
        }
    }

    private List<String> resourceList(String resource) {
        String normalized = resource == null ? "" : resource.trim();
        return normalized.isBlank() ? List.of() : List.of(normalized);
    }

    private List<String> mergeResources(List<String> current, String resource) {
        LinkedHashSet<String> result = new LinkedHashSet<>(current == null ? List.of() : current);
        String normalized = resource == null ? "" : resource.trim();
        if (!normalized.isBlank()) result.add(normalized);
        return List.copyOf(result);
    }
}
