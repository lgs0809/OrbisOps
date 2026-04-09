package cn.lgs.orbisops.domain.alert.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertAggregateSeed;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;

import java.util.List;
import java.util.Optional;

public interface IAlertAggregationRepository {

    boolean insertIfAbsent(AlertAggregateSeed seed);

    Optional<AlertAggregateSnapshot> lock(String aggregateKey);

    boolean apply(AlertAggregationPlan plan);

    List<AlertAggregateSnapshot> lockDueSummaries(int limit, int staleClaimSeconds);

    boolean claimSummary(AlertAggregateSnapshot aggregate, String claimToken, int staleClaimSeconds);

    boolean acknowledgeSummary(AlertSummaryClaim claim, int debounceSeconds);

    void releaseSummaryClaim(AlertSummaryClaim claim);
}
