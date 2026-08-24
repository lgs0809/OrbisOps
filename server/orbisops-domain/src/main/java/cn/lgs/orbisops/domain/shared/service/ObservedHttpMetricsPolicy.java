package cn.lgs.orbisops.domain.shared.service;

import java.util.*;

/** Raw scrape arithmetic and provenance checks shared by workflows and task acceptance. */
public final class ObservedHttpMetricsPolicy {
    private static final String REQUESTS = "ops04_http_requests_total";
    private static final String ERRORS = "ops04_http_errors_total";
    private static final String BUCKET = "ops04_http_request_duration_seconds_bucket";
    public Map<String, Object> evidence(Map<String, Object> window, Map<String, Object> wrapper, String kind,
                                          List<String> gaps, List<String> references) {
        if (wrapper == null || wrapper.isEmpty()) { gaps.add(kind + ":MISSING"); return Map.of(); }
        Map<String, Object> data = wrapper.containsKey("normalizedContent") ? map(wrapper.get("normalizedContent")) : wrapper;
        Map<String, Object> scope = map(data.get("scope"));
        for (String key : List.of("projectId", "environment", "serviceId", "startEpoch", "endEpoch")) {
            Object expected = window.get(key), actual = scope.get(key);
            boolean match = expected instanceof Number && actual instanceof Number
                    ? finite(expected) == finite(actual) : expected != null && expected.equals(actual);
            if (!match) fail("EVIDENCE_SCOPE_MISMATCH:" + key);
        }
        if (!kind.equals(data.get("kind"))) fail("EVIDENCE_KIND_MISMATCH");
        if (!(data.get("queryId") instanceof String id) || !id.matches("[A-Za-z0-9_-]{1,128}")) fail("EVIDENCE_ID_REQUIRED");
        references.add(String.valueOf(data.get("queryId")));
        if (!"AVAILABLE".equals(data.get("status"))) { gaps.add(kind + ":UNAVAILABLE"); return Map.of(); }
        return data;
    }

    public Map<String, Object> metricFacts(Map<String, Object> window, Map<String, Object> metrics, List<String> gaps) {
        double requests = 0, errors = 0;
        String reachable = "UNKNOWN";
        var histograms = new HashMap<String, TreeMap<Double, Double>>();
        boolean sawRequests = false, sawErrors = false;
        var versions = new java.util.TreeSet<String>();
        var routes = new java.util.TreeSet<String>();
        double start = finite(window.get("startEpoch")), end = finite(window.get("endEpoch"));
        for (Object entry : list(metrics.get("series"))) {
            Map<String, Object> series = map(entry), labels = map(series.get("metric"));
            String name = String.valueOf(labels.get("__name__"));
            List<?> values = list(series.get("values"));
            if (values.isEmpty()) continue;
            if (name.equals("up")) {
                List<?> last = list(values.get(values.size() - 1));
                if (end - finite(last.get(0)) > 10) gaps.add("TARGET_REACHABILITY_STALE");
                else if (finite(last.get(1)) == 0) reachable = "UNREACHABLE";
                else if (!reachable.equals("UNREACHABLE")) reachable = "REACHABLE";
                continue;
            }
            for (String[] pair : List.of(new String[]{"projectId", "project_id"}, new String[]{"environment", "environment"}, new String[]{"serviceId", "service_id"})) {
                if (!window.get(pair[0]).equals(labels.get(pair[1]))) fail("METRIC_SERIES_SCOPE_MISMATCH");
            }
            if (!Set.of(REQUESTS, ERRORS, BUCKET).contains(name)) fail("UNEXPECTED_METRIC");
            double delta = delta(values, start, end, gaps);
            if (name.equals(REQUESTS) && delta>0) {
                versions.add(String.valueOf(labels.get("version")));
                routes.add(String.valueOf(labels.get("route")));
            }
            if (name.equals(REQUESTS)) { requests += delta; sawRequests = true; }
            if (name.equals(ERRORS)) { errors += delta; sawErrors = true; }
            if (name.equals(BUCKET)) {
                var identity = new TreeMap<>(labels);
                identity.remove("__name__"); identity.remove("le");
                double boundary = "+Inf".equals(labels.get("le")) ? Double.POSITIVE_INFINITY : finite(labels.get("le"));
                if (histograms.computeIfAbsent(identity.toString(), ignored -> new TreeMap<>()).put(boundary, delta) != null) fail("DUPLICATE_HISTOGRAM_BUCKET");
            }
        }
        if (!sawRequests || !sawErrors) gaps.add("REQUEST_METRICS_MISSING");
        if (reachable.equals("UNKNOWN")) gaps.add("TARGET_REACHABILITY_MISSING");
        if (errors > requests) fail("ERROR_DENOMINATOR_INVALID");
        var merged = new TreeMap<Double, Double>();
        Set<Double> compatible = null;
        for (var histogram : histograms.values()) {
            if (compatible == null) compatible = Set.copyOf(histogram.keySet());
            else if (!compatible.equals(histogram.keySet())) fail("HISTOGRAM_BUCKETS_INCOMPATIBLE");
            histogram.forEach((key, value) -> merged.merge(key, value, Double::sum));
        }
        double p95 = 0;
        if (merged.size() < 2 || !merged.containsKey(Double.POSITIVE_INFINITY) || requests == 0) gaps.add("LATENCY_HISTOGRAM_MISSING");
        else {
            if (Math.abs(merged.get(Double.POSITIVE_INFINITY) - requests) > .000001) fail("HISTOGRAM_DENOMINATOR_MISMATCH");
            p95 = quantile(merged, .95);
            if (merged.lowerEntry(Double.POSITIVE_INFINITY).getValue() < requests * .95) gaps.add("LATENCY_TAIL_UNBOUNDED");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("reachability", reachable);
        result.put("sampleCountLowerBound", (long) Math.floor(requests));
        result.put("sampleCountMethod", "sum of observed counter deltas; no extrapolation or rounding up");
        result.put("errorCount", errors);
        result.put("errorRate", requests == 0 ? 0 : errors / requests);
        result.put("p95Seconds", p95);
        result.put("p95Defined", merged.size() >= 2 && requests > 0 && merged.containsKey(Double.POSITIVE_INFINITY)
                && merged.lowerEntry(Double.POSITIVE_INFINITY).getValue() >= requests * .95);
        result.put("requestMetricsDefined", sawRequests && sawErrors && requests > 0);
        result.put("qpsLowerBound", requests / (end - start));
        result.put("quantileMethod", "merge compatible classic histogram buckets then interpolate; never average instance p95");
        result.put("versions", new ArrayList<>(versions));
        result.put("routes", new ArrayList<>(routes));
        return result;
    }

    private double delta(List<?> values, double start, double end, List<String> gaps) {
        double previousTime = -1, previousValue = -1, increase = 0;
        if (values.size() < 2) gaps.add("COUNTER_SAMPLES_INSUFFICIENT");
        for (Object raw : values) {
            List<?> pair = list(raw);
            if (pair.size() != 2) fail("COUNTER_PAIR_INVALID");
            double timestamp = finite(pair.get(0)), value = finite(pair.get(1));
            if (timestamp < start || timestamp > end || value < 0 || timestamp <= previousTime) fail("COUNTER_SAMPLE_INVALID");
            if (previousTime < 0 && timestamp - start > 10 || previousTime >= 0 && timestamp - previousTime > 10) gaps.add("COUNTER_COVERAGE_GAP");
            if (previousValue >= 0) {
                if (value < previousValue) { gaps.add("COUNTER_RESET_IN_WINDOW"); }
                else increase += value - previousValue;
            }
            previousValue = value; previousTime = timestamp;
        }
        if (end - previousTime > 10) gaps.add("COUNTER_COVERAGE_GAP");
        return increase;
    }

    private double quantile(TreeMap<Double, Double> buckets, double q) {
        double total = buckets.get(Double.POSITIVE_INFINITY), rank = total * q, previousCount = 0, previousBound = 0;
        double answer = 0;
        boolean found = false;
        for (var bucket : buckets.entrySet()) {
            if (bucket.getKey() <= 0 || bucket.getValue() < previousCount) fail("HISTOGRAM_INVALID");
            if (!found && bucket.getValue() >= rank) {
                answer = Double.isInfinite(bucket.getKey()) ? previousBound : previousBound
                        + (bucket.getKey() - previousBound) * (rank - previousCount) / Math.max(1e-12, bucket.getValue() - previousCount);
                found = true;
            }
            previousCount = bucket.getValue(); previousBound = bucket.getKey();
        }
        return answer;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) { return raw instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of(); }
    private List<?> list(Object raw) { return raw instanceof List<?> value ? value : List.of(); }
    private double finite(Object raw) {
        double value;
        try { value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("OBSERVABILITY_NUMBER_INVALID"); }
        if (!Double.isFinite(value)) fail("NUMBER_NOT_FINITE");
        return value;
    }
    private void fail(String reason) { throw new IllegalArgumentException("OBSERVABILITY_" + reason); }
}
