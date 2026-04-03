package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationRetryAdjustment;

import java.util.Optional;

/** Business rule for bounded Investigation query adjustment. */
public final class InvestigationRetryPolicy {

    private static final String SOURCE_PROM = "prometheus";

    public Optional<InvestigationRetryAdjustment> adjust(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("INVESTIGATION_RETRY_INPUT_REQUIRED");
        }
        if (!input.retryRequested() || !retryableStatus(input.status())) {
            return Optional.empty();
        }
        int rangeMinutes = input.rangeMinutes();
        int expandedRange = Math.min(
                Math.max(rangeMinutes * 4, rangeMinutes + 15),
                240);
        String promWindow = input.promWindow();
        if (SOURCE_PROM.equals(input.source()) && "5m".equals(promWindow)) {
            promWindow = "15m";
        }
        return Optional.of(new InvestigationRetryAdjustment(
                expandedRange,
                promWindow));
    }

    private boolean retryableStatus(String status) {
        return "NOT_FOUND".equals(status)
                || "INSUFFICIENT".equals(status);
    }

    public record Input(String source,
                        String status,
                        boolean retryRequested,
                        Integer rangeMinutes,
                        String promWindow) {
    }
}
