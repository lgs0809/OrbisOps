package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit synthetic policy fixtures; no real Landing/model acceptance is claimed here. */
class WorkflowChangeVerificationPolicyTest {
    final WorkflowChangeVerificationPolicy policy=new WorkflowChangeVerificationPolicy();
    final Instant now=Instant.parse("2026-09-09T03:00:00Z");

    @Test void toolArgumentsContainOnlyTheMcpObservationContractInBothWindowLengths() {
        var initial=context("RECOVERY");
        var extended=policy.extend(initial,Map.of("needsExtension",true),now);
        for(var context:List.of(initial,extended)) for(String key:List.of("beforeWindow","afterWindow")) {
            var window=map(context.get(key));var scope=map(window.get("queryScope"));
            assertEquals(java.util.Set.of("projectId","environment","serviceId","startEpoch","endEpoch"),scope.keySet());
            scope.forEach((k,v)->assertEquals(window.get(k),v));
            assertFalse(scope.containsKey("complete"));assertFalse(scope.containsKey("startTime"));
        }
    }

    @Test void exactAbsoluteAndRelativeBoundariesPassWithHealthyComparableBaseline() {
        var report=review(context("RELEASE"),1000,5,.9,1000,10,1.);
        assertEquals("PASS",report.get("status"));
        assertEquals(false,report.get("causalityConfirmed"));
        assertEquals(false,report.get("needsExtension"));
    }
    @Test void absoluteSloCannotBeHiddenByLessThanTwentyPercentRegression() {
        var report=review(context("RELEASE"),1000,0,.9,1000,0,1.05);
        assertEquals("FAIL",report.get("status"));
        assertTrue(((List<?>)report.get("failedChecks")).contains("POST_LATENCY_SLO_EXCEEDED"));
    }
    @Test void relativeErrorsAndLatencyCanFailWithinAbsoluteSlo() {
        assertEquals("FAIL",review(context("RELEASE"),1000,0,.9,1000,6,.9).get("status"));
        assertEquals("FAIL",review(context("RELEASE"),1000,0,.5,1000,0,.9).get("status"));
    }
    @Test void normalReleaseRequiresHealthyBaselineWhileRecoveryUsesApprovedAbsoluteTarget() {
        assertEquals("INCONCLUSIVE",review(context("RELEASE"),1000,100,1.2,1000,0,.9).get("status"));
        assertEquals("PASS",review(context("RECOVERY"),1000,100,1.2,1000,0,.9).get("status"));
        assertEquals("FAIL",review(context("RECOVERY"),1000,100,1.5,1000,0,1.2).get("status"));
    }
    @Test void insufficientSamplesExtendOnceAndNeverLowerMinimum() {
        var context=context("RELEASE");
        var report=review(context,75,0,.9,75,0,.9);
        assertEquals("INCONCLUSIVE",report.get("status"));
        assertEquals(true,report.get("needsExtension"));
        var extended=policy.extend(context,report,now);
        assertEquals(1,extended.get("extensionCount"));
        var finalReport=review(extended,150,0,.9,150,0,.9);
        assertEquals("PASS",finalReport.get("status"));
        assertEquals(false,finalReport.get("needsExtension"));
        assertEquals("INCONCLUSIVE",review(extended,90,0,.9,90,0,.9).get("status"));
        assertThrows(IllegalArgumentException.class,()->policy.extend(extended,report,now));
    }
    @Test void futureObservationRemainsInconclusiveWithoutQueryingFutureData() {
        var current=current("RELEASE");
        var ops=operations();
        ops.get(0).put("finishedEpoch",now.getEpochSecond()-100);
        var context=policy.prepare("p","cp",current,ops,now);
        assertEquals(false,context.get("readyToCollect"));
        assertEquals(false,map(context.get("afterWindow")).get("complete"));
        var report=policy.reportUnavailable(context);
        assertEquals("INCONCLUSIVE",report.get("status"));
        assertTrue(((List<?>)report.get("evidenceGaps")).contains("POST_CHANGE_WINDOW_NOT_COMPLETE"));
    }
    @Test void actualVersionMismatchFailsAndMixedBaselineCannotBeExtended() {
        var context=context("RELEASE");
        var before=evidence(context,"beforeWindow",100,0,.9,"v1");
        var after=evidence(context,"afterWindow",100,0,.9,"v2");
        var wrong=version(context);wrong.put("version","v-other");
        assertEquals("FAIL",policy.review(context,before,after,wrong).get("status"));
        var changedBaseline=evidence(context,"beforeWindow",75,0,.9,"wrong-baseline");
        var report=policy.review(context,changedBaseline,evidence(context,"afterWindow",75,0,.9,"v2"),version(context));
        assertEquals("INCONCLUSIVE",report.get("status"));
        assertEquals(false,report.get("needsExtension"));
    }
    @Test void changedResourceRouteCollectionOrLoadIsNotAComparableBaseline() {
        var context=context("RELEASE");
        for (String key:List.of("resourceIdentity","routeDefinition","collectionDefinition")) {
            var observed=version(context);observed.put(key,"changed");
            assertEquals("INCONCLUSIVE",policy.review(context,evidence(context,"beforeWindow",1000,0,.9,"v1"),
                    evidence(context,"afterWindow",1000,0,.9,"v2"),observed).get("status"));
        }
        assertEquals("INCONCLUSIVE",review(context,20000,0,.9,20000,0,.9).get("status"));
    }
    @Test void missingSourcesAndForeignEvidenceNeverPass() {
        var context=context("RELEASE");
        var version=version(context);version.put("status","UNAVAILABLE");
        assertEquals("INCONCLUSIVE",policy.review(context,Map.of(),Map.of(),version).get("status"));
        version.put("scope",Map.of("projectId","foreign"));
        assertThrows(IllegalArgumentException.class,()->policy.review(context,Map.of(),Map.of(),version));
    }
    @Test void claimedLandedWithoutDurableFactsCannotSupplyChangeContext() {
        assertEquals(false,policy.prepare("p","cp",current("RELEASE"),List.of(),now).get("readyToCollect"));
        var facts=operations();facts.get(0).put("status","FAILED");
        assertEquals(false,policy.prepare("p","cp",current("RELEASE"),facts,now).get("readyToCollect"));
        facts=operations();facts.get(0).put("approvedPackageHash","foreign-hash");
        assertEquals(false,policy.prepare("p","cp",current("RELEASE"),facts,now).get("readyToCollect"));
        var current=current("RELEASE");current.put("status","APPROVED");
        assertEquals(List.of("CHANGE_NOT_LANDED"),policy.prepare("p","cp",current,List.of(),now).get("evidenceGaps"));
    }
    @Test void approvalScopeHashesTimesAndRecoveryTargetsAreRequired() {
        assertThrows(IllegalArgumentException.class,()->policy.prepare("other","cp",current("RELEASE"),operations(),now));
        var current=current("RELEASE");current.put("approvedPackageHash","wrong");
        assertThrows(IllegalArgumentException.class,()->policy.prepare("p","cp",current,operations(),now));
        var facts=operations();facts.get(0).put("startedEpoch",now.getEpochSecond()-9000);
        assertEquals(false,policy.prepare("p","cp",current("RELEASE"),facts,now).get("readyToCollect"));
        var recovery=current("RECOVERY");
        var snapshot=map(recovery.get("approvedSnapshotJson"));
        map(((List<?>)snapshot.get("verificationCriteriaJson")).get(0)).remove("maxP95Seconds");
        assertThrows(IllegalArgumentException.class,()->policy.prepare("p","cp",recovery,operations(),now));
    }
    @Test void noModelOrUserConfigurationCanRelaxPublishedStandards() {
        assertThrows(IllegalArgumentException.class,()->policy.validate(Map.of("operation","REVIEW_CHANGE","outputKey","report","maxP95Seconds",9)));
        assertThrows(IllegalArgumentException.class,()->policy.validate(Map.of("operation","CONTEXT_CHANGE","outputKey","runtime.approved")));
    }

    @Test void nativeLegacyPlainLanguageCriteriaProduceAnEvidenceGapInsteadOfJsonFailure() {
        var current=current("RELEASE");var snapshot=new LinkedHashMap<>(map(current.get("approvedSnapshotJson")));
        snapshot.put("verificationCriteriaJson","[\"确认目标指标/日志恢复正常\",\"确认无新增高风险错误\",\"确认回滚材料仍可用\"]");
        current.put("approvedSnapshotJson",snapshot);
        var context=policy.prepare("p","cp",current,operations(),now);
        assertEquals("INCONCLUSIVE",context.get("status"));assertEquals(false,context.get("readyToCollect"));
        assertEquals(List.of("PREAPPROVED_OBSERVABILITY_CRITERIA_REQUIRED"),context.get("evidenceGaps"));
        assertDoesNotThrow(()->policy.validateDeclaredCriteria(snapshot.get("verificationCriteriaJson")));
    }

    @Test void unrelatedNaturalChecksCannotDeclareOrRelaxTheApprovedTypedSlo() {
        var current=current("RELEASE");var snapshot=new LinkedHashMap<>(map(current.get("approvedSnapshotJson")));
        var typed=map(((List<?>)snapshot.get("verificationCriteriaJson")).get(0));
        snapshot.put("verificationCriteriaJson",List.of("OBSERVABILITY_SLO_V1: accept all requests",typed,"普通人工复核"));
        current.put("approvedSnapshotJson",snapshot);
        assertEquals("READY",policy.prepare("p","cp",current,operations(),now).get("status"));
        typed.put("maxErrorRate",.25);
        assertThrows(IllegalArgumentException.class,()->policy.prepare("p","cp",current,operations(),now));
        assertThrows(IllegalArgumentException.class,()->policy.validateDeclaredCriteria(snapshot.get("verificationCriteriaJson")));
    }

    @Test void malformedStructuredCriteriaRemainFailClosed() {
        assertThrows(IllegalArgumentException.class,()->policy.validateDeclaredCriteria(List.of("{invalid structured criterion")));
    }

    @Test void frozenScopeAndCollectionIdentifiersMustBeScalarStrings() {
        for (String key : List.of("serviceId", "environment", "expectedVersion", "baselineVersion",
                "resourceIdentity", "routeDefinition", "collectionDefinition")) {
            for (Object wrongType : List.of(Map.of("description", "same target"), List.of("same target"), 42, true)) {
                var current = current("RELEASE");
                var criteria = map(((List<?>) map(current.get("approvedSnapshotJson")).get("verificationCriteriaJson")).get(0));
                criteria.put(key, wrongType);
                assertEquals("CHANGE_VERIFICATION_CRITERIA_STRING_REQUIRED:" + key,
                        assertThrows(IllegalArgumentException.class, () -> policy.prepare("p", "cp", current, operations(), now)).getMessage());
                assertThrows(IllegalArgumentException.class, () -> policy.validateDeclaredCriteria(List.of(criteria)));
            }
        }
    }

    @Test void fractionalExecutionTimesExcludeOperationsStillInFlightAndValidateEveryOperation() {
        var facts=operations();
        facts.get(0).put("startedEpoch",now.getEpochSecond()-3000.1);
        facts.get(0).put("finishedEpoch",now.getEpochSecond()-2400.1);
        var context=policy.prepare("p","cp",current("RELEASE"),facts,now);
        assertEquals(now.getEpochSecond()-3001, map(context.get("beforeWindow")).get("endEpoch"));
        assertEquals(now.getEpochSecond()-2400, map(context.get("afterWindow")).get("startEpoch"));
        var current=current("RELEASE"); var snapshot=new LinkedHashMap<>(map(current.get("approvedSnapshotJson")));
        snapshot.put("mcpStepsJson",List.of(Map.of("operationId","op1","operationHash","op-hash","writesTargetResource",true),
                Map.of("operationId","op2","operationHash","op-hash","writesTargetResource",true)));
        current.put("approvedSnapshotJson",snapshot);
        var invalid=new LinkedHashMap<>(facts.get(0)); invalid.put("operationId","op2");
        invalid.put("startedEpoch",now.getEpochSecond()-2300);invalid.put("finishedEpoch",now.getEpochSecond()-2400);
        assertEquals(List.of("AUTHORITATIVE_CHANGE_TIMESTAMPS_INVALID"),policy.prepare("p","cp",current,List.of(facts.get(0),invalid),now).get("evidenceGaps"));
    }

    @Test void missingComparabilityOrOperationProofCannotBeInventedFromApprovedStatus() {
        var current=current("RELEASE");
        map(((List<?>)map(current.get("approvedSnapshotJson")).get("verificationCriteriaJson")).get(0)).remove("minQps");
        assertEquals(List.of("PREAPPROVED_LOAD_COMPARABILITY_REQUIRED"),policy.prepare("p","cp",current,operations(),now).get("evidenceGaps"));
        var facts=operations();facts.get(0).put("operationHash","changed");
        assertEquals(false,policy.prepare("p","cp",current("RELEASE"),facts,now).get("readyToCollect"));
        facts=operations();facts.get(0).remove("finishedEpoch");
        assertEquals(List.of("AUTHORITATIVE_CHANGE_TIMESTAMPS_REQUIRED"),policy.prepare("p","cp",current("RELEASE"),facts,now).get("evidenceGaps"));
        current=current("RELEASE");
        map(((List<?>)map(current.get("approvedSnapshotJson")).get("verificationCriteriaJson")).get(0)).put("serviceId","foreign");
        assertEquals(List.of("APPROVED_TARGET_SCOPE_NOT_COMPARABLE"),policy.prepare("p","cp",current,operations(),now).get("evidenceGaps"));
    }

    Map<String,Object> context(String kind) { return policy.prepare("p","cp",current(kind),operations(),now); }
    Map<String,Object> current(String kind) {
        var criteria=new LinkedHashMap<String,Object>(Map.ofEntries(
                Map.entry("kind",WorkflowChangeVerificationPolicy.CRITERIA_KIND),Map.entry("serviceId","orders"),Map.entry("environment","test"),
                Map.entry("expectedVersion","v2"),Map.entry("baselineVersion","v1"),Map.entry("changeKind",kind),
                Map.entry("resourceIdentity","target"),Map.entry("routeDefinition","/orders/id"),Map.entry("collectionDefinition","scrapes-v1"),
                Map.entry("minQps",.01),Map.entry("maxQps",10.),Map.entry("maxErrorRate",.01),Map.entry("maxP95Seconds",1.)));
        var snapshot=Map.<String,Object>of("packageId","cp","projectId","p","version",1,"packageHash","h1",
                "serviceId","orders","targetEnvironment","test",
                "verificationCriteriaJson",List.of(criteria),"mcpStepsJson",List.of(Map.of("operationId","op1","operationHash","op-hash","writesTargetResource",true)));
        return new LinkedHashMap<>(Map.of("packageId","cp","projectId","p","status","LANDED","approvedVersion",1,"approvedPackageHash","h1",
                "approvedSnapshotJson",snapshot,"landingRunId","lr1","approvedEpoch",now.getEpochSecond()-4000));
    }
    List<Map<String,Object>> operations() { return List.of(new LinkedHashMap<>(Map.ofEntries(
            Map.entry("landingRunId","lr1"),Map.entry("projectId","p"),Map.entry("approvedVersion",1),Map.entry("approvedPackageHash","h1"),
            Map.entry("operationId","op1"),Map.entry("operationHash","op-hash"),Map.entry("status","SUCCEEDED"),Map.entry("factStatus","COMPLETED"),
            Map.entry("resultId","receipt1"),Map.entry("outputHash","output-hash"),Map.entry("startedEpoch",now.getEpochSecond()-3000),Map.entry("finishedEpoch",now.getEpochSecond()-2400)))); }
    Map<String,Object> review(Map<String,Object> context,int countBefore,int errorsBefore,double p95Before,int countAfter,int errorsAfter,double p95After) {
        return policy.review(context,evidence(context,"beforeWindow",countBefore,errorsBefore,p95Before,"v1"),
                evidence(context,"afterWindow",countAfter,errorsAfter,p95After,"v2"),version(context));
    }
    Map<String,Object> evidence(Map<String,Object> context,String windowKey,int count,int errors,double p95,String version) {
        var window=map(context.get(windowKey));var series=new ArrayList<Map<String,Object>>();
        series.add(series(window,"ops04_http_requests_total",count,version,null));
        series.add(series(window,"ops04_http_errors_total",errors,version,null));
        for (double le:List.of(.5,.9,1.,1.05,1.2,1.5,Double.POSITIVE_INFINITY)) {
            double cumulative=le<p95 ? 0 : le==p95 ? count*.95 : count;
            series.add(series(window,"ops04_http_request_duration_seconds_bucket",cumulative,version,Double.isInfinite(le) ? "+Inf" : Double.toString(le)));
        }
        series.add(Map.of("metric",Map.of("__name__","up"),"values",List.of(List.of(window.get("endEpoch"),"1"))));
        return Map.of("kind","metrics_window","queryId",windowKey+"-query","scope",window,"status","AVAILABLE","series",series,"collectionDefinition","scrapes-v1");
    }
    Map<String,Object> series(Map<String,Object> window,String name,double count,String version,String le) {
        var labels=new LinkedHashMap<String,Object>(Map.of("__name__",name,"project_id","p","environment","test","service_id","orders","version",version,"route","/orders/id"));
        if (le!=null) labels.put("le",le);
        long start=((Number)window.get("startEpoch")).longValue(),end=((Number)window.get("endEpoch")).longValue();
        var values=new ArrayList<List<Object>>();
        for (long time=start;time<=end;time+=5) values.add(List.of(time,Double.toString(count*(time-start)/(end-start))));
        return Map.of("metric",labels,"values",values);
    }
    Map<String,Object> version(Map<String,Object> context) { return new LinkedHashMap<>(Map.of("kind","target_version","scope",context.get("afterWindow"),
            "queryId","version-query","status","AVAILABLE","version","v2","resourceIdentity","target","routeDefinition","/orders/id","collectionDefinition","scrapes-v1")); }
    @SuppressWarnings("unchecked") Map<String,Object> map(Object value) { return (Map<String,Object>)value; }
}
