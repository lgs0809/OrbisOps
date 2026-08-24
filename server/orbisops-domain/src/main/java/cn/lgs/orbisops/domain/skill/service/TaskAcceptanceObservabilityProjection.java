package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.service.ObservedHttpMetricsPolicy;
import java.util.*;

/** Versioned, deterministic view of retained raw scrapes; never trusts a provider's derived verdict. */
public final class TaskAcceptanceObservabilityProjection {
    public static final String KEY = "observedWindowV1";

    @SuppressWarnings("unchecked")
    public Map<String,Object> content(Map<String,Object> receipt) {
        var result = new LinkedHashMap<>(receipt);
        result.remove(KEY); // This namespace belongs to the verifier, not remote tools.
        if (!"metrics_window".equals(receipt.get("kind"))
                || !"ops04-completed-http-raw-scrapes-v1".equals(receipt.get("collectionDefinition"))
                || !"AVAILABLE".equals(receipt.get("status"))) return result;
        var observed = new LinkedHashMap<String,Object>();
        observed.put("evidenceComplete", false);
        try {
            var window = new LinkedHashMap<>((Map<String,Object>) receipt.get("scope"));
            long start = ((Number)window.get("startEpoch")).longValue();
            long end = ((Number)window.get("endEpoch")).longValue();
            if (end <= start) throw new IllegalArgumentException("INVALID_WINDOW");
            window.put("complete", true);
            var policy = new ObservedHttpMetricsPolicy();
            var gaps = new ArrayList<String>();
            var raw = policy.evidence(window, receipt, "metrics_window", gaps, new ArrayList<>());
            var facts = policy.metricFacts(window, raw, gaps);
            if (((Number)facts.get("sampleCountLowerBound")).longValue() < 100) gaps.add("INSUFFICIENT_REQUEST_SAMPLES");
            observed.put("evidenceComplete", gaps.isEmpty());
            observed.put("evidenceGaps", gaps);
            observed.put("windowSeconds", end-start);
            if (!gaps.contains("TARGET_REACHABILITY_STALE") && !"UNKNOWN".equals(facts.get("reachability")))
                observed.put("reachability", facts.get("reachability"));
            // A partial/reset counter must not become a trustworthy zero-error or low-latency check.
            boolean countersComplete = gaps.stream().noneMatch(g -> Set.of("COUNTER_SAMPLES_INSUFFICIENT",
                    "COUNTER_COVERAGE_GAP", "COUNTER_RESET_IN_WINDOW", "REQUEST_METRICS_MISSING").contains(g));
            if (countersComplete) {
                observed.put("sampleCountLowerBound", facts.get("sampleCountLowerBound"));
                if (Boolean.TRUE.equals(facts.get("requestMetricsDefined"))) observed.put("errorRate", facts.get("errorRate"));
                if (Boolean.TRUE.equals(facts.get("p95Defined"))) observed.put("p95Seconds", facts.get("p95Seconds"));
            }
        } catch (RuntimeException malformed) {
            observed.clear();
            observed.put("evidenceComplete", false);
            observed.put("evidenceGaps", List.of("RAW_METRICS_INVALID"));
        }
        result.put(KEY, observed);
        return result;
    }
}
