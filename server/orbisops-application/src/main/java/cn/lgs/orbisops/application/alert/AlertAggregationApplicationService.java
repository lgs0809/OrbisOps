package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertAggregationRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSeed;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationAction;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationSignal;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;
import cn.lgs.orbisops.domain.alert.service.AlertAggregationPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AlertAggregationApplicationService {

    private final IAlertAggregationRepository aggregates;
    private final Supplier<String> claimIdSupplier;
    private final AlertAggregationTransactionPort transactions;
    private final AlertAggregationPolicy policy;

    public AlertAggregationApplicationService(
            IAlertAggregationRepository aggregates,
            Supplier<String> claimIdSupplier,
            AlertAggregationTransactionPort transactions) {
        if (aggregates == null) throw new IllegalArgumentException("ALERT_AGGREGATION_REPOSITORY_REQUIRED");
        if (claimIdSupplier == null) throw new IllegalArgumentException("ALERT_AGGREGATION_CLAIM_ID_SUPPLIER_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("ALERT_AGGREGATION_TRANSACTION_REQUIRED");
        this.aggregates = aggregates;
        this.claimIdSupplier = claimIdSupplier;
        this.transactions = transactions;
        this.policy = new AlertAggregationPolicy();
    }

    public AlertAggregationDecision record(
            String projectId,
            long ruleId,
            String fingerprint,
            String alertStatus,
            String severity,
            String affectedResource,
            Map<String, Object> rawAlert,
            int debounceSeconds,
            int maxWaitSeconds) {
        AlertAggregationSignal signal = policy.signal(
                projectId,
                ruleId,
                fingerprint,
                alertStatus,
                severity,
                affectedResource,
                rawAlert,
                debounceSeconds,
                maxWaitSeconds);
        AlertAggregateSeed seed = policy.seed(signal);
        return transactions.required(() -> {
            // Existing aggregates must acquire an exclusive lock before any insert.
            // INSERT IGNORE on an existing InnoDB key first acquires a shared lock;
            // concurrent duplicates then deadlock while upgrading it to FOR UPDATE.
            AlertAggregateSnapshot current = aggregates.lock(seed.aggregateKey()).orElse(null);
            boolean inserted = false;
            if (current == null) {
                inserted = aggregates.insertIfAbsent(seed);
                current = aggregates.lock(seed.aggregateKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "ALERT_AGGREGATE_WRITE_FAILED:聚合记录不存在"));
            }
            AlertAggregationPlan plan = policy.plan(inserted, current, signal);
            if (plan.action() != AlertAggregationAction.NONE && !aggregates.apply(plan)) {
                throw new IllegalStateException("ALERT_AGGREGATE_VERSION_CONFLICT:" + plan.aggregateKey());
            }
            AlertAggregateSnapshot result = plan.action() == AlertAggregationAction.NONE
                    ? current
                    : aggregates.lock(seed.aggregateKey())
                            .orElseThrow(() -> new IllegalStateException("ALERT_AGGREGATE_READ_FAILED"));
            return policy.decision(result, plan.eventType());
        });
    }

    public List<AlertSummaryClaim> claimDueSummaries(int limit, int staleClaimSeconds) {
        int safeLimit = policy.claimLimit(limit);
        int safeStale = policy.staleClaimSeconds(staleClaimSeconds);
        return transactions.required(() -> {
            List<AlertAggregateSnapshot> due = aggregates.lockDueSummaries(safeLimit, safeStale);
            List<AlertSummaryClaim> claimed = new ArrayList<>();
            for (AlertAggregateSnapshot aggregate : due) {
                String claimId = claimIdSupplier.get();
                if (aggregates.claimSummary(aggregate, claimId, safeStale)) {
                    claimed.add(policy.claim(aggregate, claimId));
                }
            }
            return List.copyOf(claimed);
        });
    }

    public void acknowledgeSummary(AlertSummaryClaim claim, int debounceSeconds) {
        if (claim == null) throw new IllegalArgumentException("ALERT_SUMMARY_CLAIM_REQUIRED");
        int safeDebounce = policy.debounceSeconds(debounceSeconds);
        transactions.required(() -> {
            if (!aggregates.acknowledgeSummary(claim, safeDebounce)) {
                throw new IllegalStateException("ALERT_SUMMARY_ACK_CONFLICT:" + claim.aggregateKey());
            }
            return Boolean.TRUE;
        });
    }

    public void releaseSummaryClaim(AlertSummaryClaim claim) {
        if (claim == null) throw new IllegalArgumentException("ALERT_SUMMARY_CLAIM_REQUIRED");
        transactions.required(() -> {
            aggregates.releaseSummaryClaim(claim);
            return Boolean.TRUE;
        });
    }
}
