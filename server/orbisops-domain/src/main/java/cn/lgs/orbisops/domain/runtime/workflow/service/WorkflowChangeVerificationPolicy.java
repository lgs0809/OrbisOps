package cn.lgs.orbisops.domain.runtime.workflow.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** C's frozen observation contract. Inputs here are repository snapshots, never claimed approval fields. */
public final class WorkflowChangeVerificationPolicy {
    public static final String CRITERIA_KIND = "OBSERVABILITY_SLO_V1";

    public void validate(Map<?, ?> config) {
        String operation = String.valueOf(config.get("operation"));
        if (!Set.of("SELECT_CHANGE_REQUEST", "CONTEXT_CHANGE", "REVIEW_CHANGE", "EXTEND_CHANGE", "REPORT_CHANGE").contains(operation)) fail("OPERATION_INVALID");
        Set<String> keys = Set.of("operation", "outputKey", "contextKey", "beforeKey", "afterKey", "versionKey", "reportKey");
        if (!keys.containsAll(config.keySet())) fail("UNKNOWN_CONFIG");
        config.forEach((key,value) -> {
            if (!"operation".equals(key) && (!(value instanceof String s) || !s.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))) fail("CONFIG_KEY_INVALID");
        });
        if (!config.containsKey("outputKey")) fail("OUTPUT_KEY_REQUIRED");
        if (!Set.of("CONTEXT_CHANGE", "SELECT_CHANGE_REQUEST").contains(operation) && !config.containsKey("contextKey")) fail("CONTEXT_KEY_REQUIRED");
        if (operation.equals("REVIEW_CHANGE") && !config.keySet().containsAll(Set.of("beforeKey", "afterKey", "versionKey"))) fail("EVIDENCE_KEYS_REQUIRED");
        if (operation.equals("EXTEND_CHANGE") && !config.containsKey("reportKey")) fail("REPORT_KEY_REQUIRED");
    }

    public Map<String,Object> unavailable(String packageId, String reason) {
        return Map.of("status","INCONCLUSIVE","readyToCollect",false,"changeRef",Map.of("packageId",packageId),
                "evidenceGaps",List.of(reason),"extensionCount",0);
    }

    public Map<String,Object> prepare(String project, String packageId, Map<String,Object> current,
                                      List<Map<String,Object>> operations, Instant now) {
        if (!project.equals(current.get("projectId")) || !packageId.equals(current.get("packageId"))) fail("PROJECT_OR_PACKAGE_MISMATCH");
        if (!"LANDED".equals(current.get("status"))) return unavailable(packageId,"CHANGE_NOT_LANDED");
        var snapshot = map(current.get("approvedSnapshotJson"));
        String hash = text(current.get("approvedPackageHash"));
        if (hash.isBlank() || !hash.equals(snapshot.get("packageHash"))
                || !project.equals(snapshot.get("projectId")) || !packageId.equals(snapshot.get("packageId"))
                || number(current.get("approvedVersion")) != number(snapshot.get("version"))) fail("APPROVED_SNAPSHOT_MISMATCH");
        var candidates = structuredCriteria(snapshot.get("verificationCriteriaJson")).stream()
                .filter(item -> CRITERIA_KIND.equals(item.get("kind"))).toList();
        if (candidates.size()!=1) return unavailable(packageId,"PREAPPROVED_OBSERVABILITY_CRITERIA_REQUIRED");
        var criteria = validateCriteria(candidates.get(0));
        if (!text(snapshot.get("serviceId")).equals(criteria.get("serviceId"))
                || !text(snapshot.get("targetEnvironment")).equals(criteria.get("environment"))) {
            return unavailable(packageId,"APPROVED_TARGET_SCOPE_NOT_COMPARABLE");
        }
        if (!criteria.containsKey("minQps") || !criteria.containsKey("maxQps")) {
            return unavailable(packageId,"PREAPPROVED_LOAD_COMPARABILITY_REQUIRED");
        }
        String run = text(current.get("landingRunId"));
        var facts = operations.stream().filter(op -> run.equals(op.get("landingRunId"))
                && project.equals(op.get("projectId")) && hash.equals(op.get("approvedPackageHash"))
                && number(current.get("approvedVersion")) == number(op.get("approvedVersion"))).toList();
        // A model's LANDED line alone supplies neither execution facts nor observation boundaries.
        Map<String,String> approvedOperations = operations(snapshot);
        Set<String> approvedIds = approvedOperations.keySet();
        Set<String> completedIds = facts.stream().filter(op -> "SUCCEEDED".equals(op.get("status"))
                        && "COMPLETED".equals(op.get("factStatus")) && !text(op.get("resultId")).isBlank()
                        && !text(op.get("outputHash")).isBlank()
                        && !text(op.get("operationHash")).isBlank()
                        && text(op.get("operationHash")).equals(approvedOperations.get(text(op.get("operationId")))))
                .map(op -> text(op.get("operationId"))).collect(java.util.stream.Collectors.toSet());
        if (run.isBlank() || approvedIds.isEmpty() || !completedIds.equals(approvedIds) || facts.size()!=approvedIds.size()) {
            return unavailable(packageId,"AUTHORITATIVE_LANDING_FACTS_INCOMPLETE");
        }
        if (!(current.get("approvedEpoch") instanceof Number) || facts.stream().anyMatch(op ->
                !(op.get("startedEpoch") instanceof Number) || !(op.get("finishedEpoch") instanceof Number))) {
            return unavailable(packageId,"AUTHORITATIVE_CHANGE_TIMESTAMPS_REQUIRED");
        }
        double began = facts.stream().mapToDouble(op -> number(op.get("startedEpoch"))).min().orElse(0);
        double finished = facts.stream().mapToDouble(op -> number(op.get("finishedEpoch"))).max().orElse(0);
        double approved = number(current.get("approvedEpoch"));
        double nowEpoch = now.toEpochMilli()/1000.;
        if (approved<=0 || began<approved || finished<began || finished>nowEpoch || began<nowEpoch-6*86400
                || facts.stream().anyMatch(op -> number(op.get("finishedEpoch"))<number(op.get("startedEpoch")))) {
            return unavailable(packageId,"AUTHORITATIVE_CHANGE_TIMESTAMPS_INVALID");
        }
        var context = new LinkedHashMap<String,Object>();
        context.put("criteria",criteria);
        context.put("projectId",project);
        context.put("changeRef",Map.of("packageId",packageId,"approvedVersion",current.get("approvedVersion"),
                "approvedPackageHash",hash,"landingRunId",run,"startedEpoch",(long)Math.floor(began),"finishedEpoch",(long)Math.ceil(finished)));
        context.put("extensionCount",0);
        return windows(context,now,900);
    }

    public Map<String,Object> extend(Map<String,Object> context, Map<String,Object> previous, Instant now) {
        if (number(context.get("extensionCount"))!=0 || !Boolean.TRUE.equals(previous.get("needsExtension"))) fail("EXTENSION_NOT_ALLOWED");
        var extended = new LinkedHashMap<>(context);
        extended.put("extensionCount",1);
        return windows(extended,now,1800);
    }

    private Map<String,Object> windows(Map<String,Object> context, Instant now, int duration) {
        var result = new LinkedHashMap<>(context);
        var reference = map(context.get("changeRef"));
        long began = (long)number(reference.get("startedEpoch")), finished = (long)number(reference.get("finishedEpoch"));
        boolean ready = now.getEpochSecond() >= finished+duration;
        result.put("status",ready ? "READY" : "INCONCLUSIVE");
        result.put("readyToCollect",ready);
        result.put("beforeWindow",window(context,began-duration,began,true));
        result.put("afterWindow",window(context,finished,Math.max(finished,Math.min(finished+duration,now.getEpochSecond())),ready));
        result.put("evidenceGaps",ready ? List.of() : List.of("POST_CHANGE_WINDOW_NOT_COMPLETE"));
        result.put("nextObservationAt",Instant.ofEpochSecond(finished+duration).toString());
        return result;
    }

    private Map<String,Object> window(Map<String,Object> context,long start,long end,boolean complete) {
        var criteria = map(context.get("criteria"));
        var queryScope = Map.of("projectId",context.get("projectId"),"environment",criteria.get("environment"),
                "serviceId",criteria.get("serviceId"),"startEpoch",start,"endEpoch",end);
        return Map.of("projectId",context.get("projectId"),"environment",criteria.get("environment"),"serviceId",criteria.get("serviceId"),
                "startEpoch",start,"endEpoch",end,"startTime",Instant.ofEpochSecond(start).toString(),"endTime",Instant.ofEpochSecond(end).toString(),
                "complete",complete,"queryScope",queryScope);
    }

    public Map<String,Object> reportUnavailable(Map<String,Object> context) {
        if (Boolean.TRUE.equals(context.get("readyToCollect"))) fail("COLLECTED_EVIDENCE_REQUIRED");
        var report = new LinkedHashMap<>(context);
        report.put("status","INCONCLUSIVE");
        report.put("needsExtension",false);
        report.put("nextStep","补齐权威变更记录或等待观察窗口；不能据执行状态宣布验收通过");
        return report;
    }

    public Map<String,Object> review(Map<String,Object> context, Map<String,Object> beforeEvidence,
                                    Map<String,Object> afterEvidence, Map<String,Object> versionEvidence) {
        if (!Boolean.TRUE.equals(context.get("readyToCollect"))) return reportUnavailable(context);
        var policy = new WorkflowObservabilityPolicy();
        var before = policy.review("REVIEW_INSPECTION",map(context.get("beforeWindow")),beforeEvidence,Map.of(),Map.of());
        var after = policy.review("REVIEW_INSPECTION",map(context.get("afterWindow")),afterEvidence,Map.of(),Map.of());
        var b = map(before.get("metrics")); var a = map(after.get("metrics")); var criteria = map(context.get("criteria"));
        var version = normalized(versionEvidence);
        var gaps = new ArrayList<String>();
        var failures = new ArrayList<String>();
        var refs = new ArrayList<Object>();
        refs.addAll(list(before.get("evidenceReferences"))); refs.addAll(list(after.get("evidenceReferences")));
        if (!"target_version".equals(version.get("kind")) || !map(context.get("afterWindow")).entrySet().stream()
                .filter(e -> Set.of("projectId","environment","serviceId","startEpoch","endEpoch").contains(e.getKey()))
                .allMatch(e -> equal(e.getValue(),map(version.get("scope")).get(e.getKey())))) fail("VERSION_EVIDENCE_SCOPE_MISMATCH");
        if (text(version.get("queryId")).isBlank()) fail("VERSION_EVIDENCE_ID_REQUIRED");
        refs.add(version.get("queryId"));
        if (!"AVAILABLE".equals(version.get("status"))) gaps.add("VERSION_INTERFACE_UNAVAILABLE");
        else if (!criteria.get("expectedVersion").equals(version.get("version"))) failures.add("ACTUAL_VERSION_MISMATCH");
        for (String key : List.of("resourceIdentity","routeDefinition","collectionDefinition")) {
            if (!criteria.get(key).equals(version.get(key))) gaps.add("CONDITIONS_CHANGED:"+key);
        }
        for (var pair : List.of(Map.entry("before",before), Map.entry("after",after))) {
            list(pair.getValue().get("evidenceGaps")).forEach(gap -> gaps.add(pair.getKey()+":"+gap));
            if (pair.getKey().equals("after") && "UNREACHABLE".equals(pair.getValue().get("status"))) failures.add("after:TARGET_UNREACHABLE");
        }
        boolean lowBefore = number(b.get("sampleCountLowerBound"))<100, lowAfter = number(a.get("sampleCountLowerBound"))<100;
        boolean baselineVersion = list(b.get("versions")).equals(List.of(criteria.get("baselineVersion")));
        boolean afterVersion = list(a.get("versions")).equals(List.of(criteria.get("expectedVersion")));
        if (!baselineVersion) gaps.add("BASELINE_VERSION_NOT_COMPARABLE");
        if (!afterVersion) gaps.add("POST_WINDOW_VERSION_NOT_COMPARABLE");
        for (var pair : List.of(Map.entry("before",b),Map.entry("after",a))) {
            double qps = number(pair.getValue().get("qpsLowerBound"));
            if (qps<number(criteria.get("minQps")) || qps>number(criteria.get("maxQps"))) gaps.add(pair.getKey()+":PREAPPROVED_LOAD_RANGE_NOT_MET");
            if (!list(pair.getValue().get("routes")).equals(List.of(criteria.get("routeDefinition")))) gaps.add(pair.getKey()+":ROUTE_NOT_COMPARABLE");
        }
        for (var evidence : List.of(beforeEvidence,afterEvidence)) {
            if (!criteria.get("collectionDefinition").equals(normalized(evidence).get("collectionDefinition"))) gaps.add("COLLECTION_NOT_COMPARABLE");
        }
        double errorDelta = number(a.get("errorRate"))-number(b.get("errorRate"));
        double p95Base = number(b.get("p95Seconds")), p95After = number(a.get("p95Seconds"));
        boolean validAfter = list(after.get("evidenceGaps")).isEmpty();
        if (validAfter && !lowAfter && Boolean.TRUE.equals(a.get("requestMetricsDefined")) && number(a.get("errorRate"))>number(criteria.get("maxErrorRate"))) failures.add("POST_ERROR_SLO_EXCEEDED");
        if (validAfter && !lowAfter && Boolean.TRUE.equals(a.get("p95Defined")) && p95After>number(criteria.get("maxP95Seconds"))) failures.add("POST_LATENCY_SLO_EXCEEDED");
        if ("RELEASE".equals(criteria.get("changeKind"))) {
            if (!"HEALTHY".equals(before.get("status"))) gaps.add("NORMAL_RELEASE_REQUIRES_HEALTHY_BASELINE");
            if (!lowBefore && !lowAfter && baselineVersion && afterVersion && gaps.isEmpty()) {
                if (errorDelta>.005+1e-12) failures.add("ERROR_RATE_REGRESSION_EXCEEDED");
                if (p95Base<=0) gaps.add("BASELINE_P95_NOT_COMPARABLE");
                else if (p95After>p95Base*1.2+1e-12) failures.add("P95_REGRESSION_EXCEEDED");
            }
        }
        // An extension cannot repair a changed baseline version/route/collection or hide a failed SLO.
        boolean extend = number(context.get("extensionCount"))==0 && (lowBefore || lowAfter) && failures.isEmpty()
                && baselineVersion && afterVersion && gaps.stream().allMatch(g -> g.endsWith("INSUFFICIENT_REQUEST_SAMPLES")
                || g.equals("NORMAL_RELEASE_REQUIRES_HEALTHY_BASELINE"));
        var result = new LinkedHashMap<String,Object>();
        result.put("status",!failures.isEmpty() ? "FAIL" : !gaps.isEmpty() ? "INCONCLUSIVE" : "PASS");
        result.put("changeRef",context.get("changeRef")); result.put("criteria",criteria);
        result.put("before",before); result.put("after",after); result.put("actualVersion",version.getOrDefault("version","unknown"));
        result.put("evidenceGaps",List.copyOf(new java.util.LinkedHashSet<>(gaps))); result.put("failedChecks",failures);
        result.put("evidenceReferences",refs); result.put("extensionCount",context.get("extensionCount")); result.put("needsExtension",extend);
        result.put("relativeChanges",Map.of("errorRatePercentagePoints",errorDelta*100,"p95Ratio",p95Base<=0 ? "undefined" : p95After/p95Base));
        result.put("nextStep",extend ? "允许同版本、同条件扩窗至三十分钟一次" : "保存约定窗口内结论；异常或缺口交人工调查，不自动回滚");
        result.put("causalityConfirmed",false);
        return result;
    }

    public Map<String,Object> validateCriteria(Map<String,Object> source) {
        var result = new LinkedHashMap<>(source);
        for (String key : List.of("serviceId","environment","expectedVersion","baselineVersion","resourceIdentity","routeDefinition","collectionDefinition")) {
            if (!(source.get(key) instanceof String value) || value.isBlank()) fail("CRITERIA_STRING_REQUIRED:"+key);
        }
        if (!Set.of("RELEASE","RECOVERY").contains(text(source.get("changeKind")))) fail("CHANGE_KIND_REQUIRED");
        // Thresholds may be stricter than this graph's published defaults, never silently looser.
        double error = source.containsKey("maxErrorRate") ? number(source.get("maxErrorRate")) : .01;
        double latency = source.containsKey("maxP95Seconds") ? number(source.get("maxP95Seconds")) : 1.;
        if (error<0 || error>.01 || latency<=0 || latency>1) fail("APPROVED_SLO_OUTSIDE_PUBLISHED_BOUNDARY");
        if (source.containsKey("minQps") && source.containsKey("maxQps") && (number(source.get("minQps"))<0
                || number(source.get("maxQps"))<=number(source.get("minQps")))) fail("PREAPPROVED_LOAD_RANGE_INVALID");
        if ("RECOVERY".equals(source.get("changeKind")) && (!source.containsKey("maxErrorRate") || !source.containsKey("maxP95Seconds"))) fail("PREAPPROVED_RECOVERY_TARGET_REQUIRED");
        result.put("maxErrorRate",error); result.put("maxP95Seconds",latency); result.put("minimumRequests",100);
        return result;
    }

    /** Validates this frozen contract at both PREPARE/revision and observation time. */
    public void validateDeclaredCriteria(Object source) {
        // Other existing verification kinds keep their own validators.
        for (var criteria : structuredCriteria(source)) {
            if (CRITERIA_KIND.equals(criteria.get("kind"))) validateCriteria(criteria);
        }
    }

    private List<Map<String,Object>> structuredCriteria(Object source) {
        // Legacy plain-language checks are valid package material, but cannot declare this typed SLO contract.
        return list(source).stream().filter(value -> value instanceof Map<?,?>
                || value instanceof String text && text.stripLeading().startsWith("{"))
                .map(this::map).toList();
    }

    private Map<String,String> operations(Map<String,Object> snapshot) {
        var plan = map(snapshot.getOrDefault("landingPlan",snapshot.get("landingPlanJson"))); var preferred = map(plan.get("preferredPlan"));
        for (var source : List.of(preferred,plan,snapshot)) {
            for (String key : List.of("steps","operations","mcpSteps","mcpStepsJson")) {
                var values = list(source.get(key));
                if (!values.isEmpty()) {
                    var result = new LinkedHashMap<String,String>();
                    for (var value : values) {
                        var op = map(value);
                        if (!new cn.lgs.orbisops.domain.changepackage.service.ChangePackageLandingOperationSafetyPolicy().targetWrite(op)) continue;
                        String id = text(op.getOrDefault("operationId",op.get("operation_id")));
                        String hash = text(op.getOrDefault("operationHash",op.get("operation_hash")));
                        if (id.isBlank() || hash.isBlank() || result.putIfAbsent(id,hash)!=null) return Map.of();
                    }
                    return result;
                }
            }
        }
        return Map.of();
    }
    private Map<String,Object> normalized(Map<String,Object> value) { return value.containsKey("normalizedContent") ? map(value.get("normalizedContent")) : value; }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) {
        if (value instanceof String s && !s.isBlank()) value = CanonicalJson.parseObject(s);
        return value instanceof Map<?,?> m ? (Map<String,Object>)m : Map.of();
    }
    private List<?> list(Object value) {
        if (value instanceof String s && !s.isBlank()) value = CanonicalJson.parseObject("{\"items\":"+s+"}").get("items");
        return value instanceof List<?> l ? l : List.of();
    }
    private boolean equal(Object left,Object right) { return left instanceof Number && right instanceof Number ? number(left)==number(right) : left!=null && left.equals(right); }
    private double number(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) fail("FINITE_NUMBER_REQUIRED");
        return ((Number)value).doubleValue();
    }
    private String text(Object value) { return value==null ? "" : String.valueOf(value).trim(); }
    private void fail(String reason) { throw new IllegalArgumentException("CHANGE_VERIFICATION_"+reason); }
}
