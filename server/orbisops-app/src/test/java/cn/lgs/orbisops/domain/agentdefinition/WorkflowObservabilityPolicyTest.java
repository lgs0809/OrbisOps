package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowObservabilityPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic boundary fixtures; actual deployment samples are checked separately. */
class WorkflowObservabilityPolicyTest {
    private final WorkflowObservabilityPolicy policy = new WorkflowObservabilityPolicy();
    private final Instant now = Instant.parse("2026-09-09T00:30:00Z");
    private final Map<String, Object> input = Map.of("projectId", "p", "environment", "test", "serviceId", "orders");

    @Test void freezesOnlyProducedAlertWindowAndRejectsFutureOrForeignIdentity() {
        var args = new HashMap<>(input); args.put("alertTime", "2026-09-09T00:25:00Z");
        var actual = policy.window("WINDOW_ALERT", args, "p", now);
        assertEquals(now.getEpochSecond(), actual.get("endEpoch"));
        assertEquals(600L, actual.get("missingFutureSeconds"));
        assertEquals(false, actual.get("complete"));
        assertEquals(Instant.parse("2026-09-09T00:10:00Z").getEpochSecond(), actual.get("startEpoch"));
        assertThrows(IllegalArgumentException.class, () -> policy.window("WINDOW_ALERT", args, "q", now));
        args.put("alertTime", "2026-09-09T00:31:00Z");
        assertThrows(IllegalArgumentException.class, () -> policy.window("WINDOW_ALERT", args, "p", now));
    }

    @Test void exactThresholdsPassButAbsoluteErrorsAndLatencyDoNot() {
        assertEquals("HEALTHY", inspect(100,1,95,100,1).get("status"));
        assertEquals("UNHEALTHY", inspect(100,2,95,100,1).get("status"));
        assertEquals("UNHEALTHY", inspect(100,0,90,100,1).get("status"));
    }

    @Test void lowSamplesZeroTrafficMissingSeriesAndUnboundedTailNeverPass() {
        assertEquals("INCONCLUSIVE", inspect(99,0,99,99,1).get("status"));
        assertEquals("INCONCLUSIVE", inspect(0,0,0,0,1).get("status"));
        assertEquals("INCONCLUSIVE", inspect(100,0,80,90,1).get("status"));
        assertEquals("INCONCLUSIVE", policy.review("REVIEW_INSPECTION", window(), Map.of(),Map.of(),Map.of()).get("status"));
    }

    @Test void unreachableIsIndependentOfTrafficAndUnknownData() {
        assertEquals("UNREACHABLE", inspect(0,0,0,0,0).get("status"));
    }

    @Test void samplingGapsAndResetsRemainInconclusiveWithoutExtrapolatingCounts() {
        var rows = series(100,0,100,100,1);
        var first = new HashMap<>(rows.get(0));
        first.put("values", List.of(List.of(start(), "0"),List.of(end(),"100")));
        rows.set(0, first);
        assertEquals("INCONCLUSIVE", review(rows).get("status"));
        rows = series(100,0,100,100,1);
        for (var row : rows) {
            if (((Map<?,?>) row.get("metric")).get("__name__").equals("up")) continue;
            var values = new ArrayList<Object>((List<?>) row.get("values"));
            values.set(30,List.of(start()+150,"0"));
            row.put("values", values);
        }
        // Reset counters cannot silently inflate samples into a healthy verdict.
        assertEquals("INCONCLUSIVE", review(rows).get("status"));
    }

    @Test void scopeUnitAndHistogramCompatibilityAreValidated() {
        var metrics = envelope("metrics_window", Map.of("series",series(100,0,100,100,1)));
        metrics.put("scope",Map.of("projectId","other"));
        assertThrows(IllegalArgumentException.class, () -> policy.review("REVIEW_INSPECTION",window(),metrics,Map.of(),Map.of()));
        var rows = series(100,0,100,100,1);
        ((Map<String,Object>) rows.get(0).get("metric")).put("service_id","other");
        assertThrows(IllegalArgumentException.class, () -> review(rows));
        var incompatible = series(100,0,100,100,1);
        incompatible.add(row("ops04_http_request_duration_seconds_bucket",50,"0.75","second"));
        assertThrows(IllegalArgumentException.class, () -> review(incompatible));
        var nan = series(100,0,100,100,1);
        nan.get(0).put("values",List.of(List.of(start(),"NaN"),List.of(end(),"100")));
        assertThrows(IllegalArgumentException.class, () -> review(nan));
    }

    @Test void histogramAggregationUsesCombinedBucketsInsteadOfMeanInstancePercentile() {
        var rows = series(99,0,99,99,1);
        rows.add(row("ops04_http_requests_total",1,null,"second"));
        rows.add(row("ops04_http_errors_total",0,null,"second"));
        rows.add(row("ops04_http_request_duration_seconds_bucket",0,"1.0","second"));
        rows.add(row("ops04_http_request_duration_seconds_bucket",1,"1.5","second"));
        rows.add(row("ops04_http_request_duration_seconds_bucket",1,"+Inf","second"));
        var result = review(rows);
        assertEquals("HEALTHY",result.get("status"));
        assertEquals(95.0/99, ((Map<?,?>)result.get("metrics")).get("p95Seconds"));
    }

    @Test void alertReviewSeparatesConflictMissingSourceSlowSqlAndFutureWindow() {
        var metrics = envelope("metrics_window",Map.of("series",series(100,10,100,100,1)));
        var logs = envelope("logs_window",Map.of("sampleCount",100,"errorCount",0,"slowSqlCount",3,"complete",true));
        assertEquals("CONFLICT",policy.review("REVIEW_ALERT",window(),metrics,logs,Map.of()).get("status"));
        logs.put("errorCount",10);
        var preliminary = policy.review("REVIEW_ALERT",window(),metrics,logs,Map.of());
        assertEquals(true,preliminary.get("needsSql"));
        var sql = envelope("sql_window",Map.of("slowSqlCount",3,"complete",true));
        var finalReview = policy.review("REVIEW_ALERT",window(),metrics,logs,sql);
        assertEquals("FOUND",finalReview.get("slowSqlFinding"));
        assertEquals(false,finalReview.get("rootCauseConfirmed"));
        var partial = new HashMap<>(window()); partial.put("complete",false);
        assertEquals("INCONCLUSIVE",policy.review("REVIEW_ALERT",partial,metrics,logs,sql).get("status"));
        logs.put("status","UNAVAILABLE");
        assertEquals("INCONCLUSIVE",policy.review("REVIEW_ALERT",window(),metrics,logs,sql).get("status"));
    }

    @Test void policyConfigCannotOverrideThresholdsOrReachAuthority() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(Map.of("operation","REVIEW_ALERT","outputKey","x","maxErrorRate",1)));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(Map.of("operation","WINDOW_ALERT","outputKey","runtime.landingApproved")));
    }

    @Test void inspectionHandoffPreservesEvidenceAndFreezesAlertAtInspectionEnd() {
        var report = inspect(100,2,100,100,1);
        var handoff = policy.alertFromInspection(report,"p","source-run");
        assertEquals(now.toString(),handoff.get("alertTime"));
        assertEquals(report,((Map<?,?>)handoff.get("priorEvidence")).get("inspectionReport"));
        assertEquals("WINDOW_DIFFERS",((Map<?,?>)handoff.get("priorEvidence")).get("reuseDecision"));
        var alertWindow = policy.window("WINDOW_ALERT",handoff,"p",now.plusSeconds(5));
        assertEquals(now.getEpochSecond()-900,alertWindow.get("startEpoch"));
        assertEquals(now.getEpochSecond()+5,alertWindow.get("endEpoch"));
        assertEquals(false,alertWindow.get("complete"));
        assertEquals(895L,alertWindow.get("missingFutureSeconds"));
        assertThrows(IllegalArgumentException.class, () -> policy.alertFromInspection(report,"other","source-run"));
        assertThrows(IllegalArgumentException.class, () -> policy.alertFromInspection(inspect(99,0,99,99,1),"p","source-run"));
        assertThrows(IllegalArgumentException.class, () -> policy.alertFromInspection(inspect(100,0,100,100,1),"p","source-run"));
    }

    @Test void childCompletionNeverOverwritesOriginalInspectionVerdictOrWindow() {
        var report = inspect(100,2,100,100,1);
        var child = Map.<String,Object>of("executionStatus","SUCCEEDED","childRunId","child-run","workflowDefinitionHash","hash",
                "output","investigation inconclusive; waiting for future window");
        var summary = policy.summarizeInspection(report,child);
        assertEquals("UNHEALTHY",summary.get("status"));
        assertEquals(report.get("window"),summary.get("window"));
        assertEquals(report.get("evidenceReferences"),summary.get("evidenceReferences"));
        assertEquals(child,summary.get("investigation"));
        assertFalse(report.containsKey("investigation"));
        assertThrows(IllegalArgumentException.class, () -> policy.summarizeInspection(report,Map.of("executionStatus","FAILED")));
    }

    private Map<String,Object> inspect(int count,int errors,int bucket1,int bucket15,int up) { return review(series(count,errors,bucket1,bucket15,up)); }
    private Map<String,Object> review(List<Map<String,Object>> series) { return policy.review("REVIEW_INSPECTION",window(),envelope("metrics_window",Map.of("series",series)),Map.of(),Map.of()); }
    private Map<String,Object> window() { return policy.window("WINDOW_INSPECTION",input,"p",now); }
    private long start() { return now.getEpochSecond()-300; }
    private long end() { return now.getEpochSecond(); }
    private Map<String,Object> envelope(String kind,Map<String,Object> data) {
        var result = new HashMap<>(data); result.putAll(Map.of("kind",kind,"scope",window(),"queryId",kind+"-test","status","AVAILABLE")); return result;
    }
    private List<Map<String,Object>> series(int count,int errors,int bucket1,int bucket15,int up) {
        var result = new ArrayList<Map<String,Object>>();
        result.add(row("ops04_http_requests_total",count,null,"first"));
        result.add(row("ops04_http_errors_total",errors,null,"first"));
        result.add(row("ops04_http_request_duration_seconds_bucket",bucket1,"1.0","first"));
        result.add(row("ops04_http_request_duration_seconds_bucket",bucket15,"1.5","first"));
        result.add(row("ops04_http_request_duration_seconds_bucket",count,"+Inf","first"));
        result.add(new HashMap<>(Map.of("metric",Map.of("__name__","up"),"values",List.of(List.of(end(),String.valueOf(up))))));
        return result;
    }
    private Map<String,Object> row(String name,int total,String bound,String instance) {
        var labels = new HashMap<String,Object>(Map.of("__name__",name,"project_id","p","environment","test","service_id","orders","version","v1","instance",instance));
        if(bound!=null) labels.put("le",bound);
        var values = new ArrayList<List<Object>>();
        for(int i=0;i<=60;i++) values.add(List.of(start()+i*5,String.valueOf((int)Math.floor(total*i/60.))));
        return new HashMap<>(Map.of("metric",labels,"values",values));
    }
}
