package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import cn.lgs.orbisops.domain.skill.service.TaskAcceptancePolicy;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskAcceptancePolicyTest {
    final TaskAcceptancePolicy policy=new TaskAcceptancePolicy();
    TaskAcceptanceRequest.Criterion criterion(String pointer,String op,Object expected) {return new TaskAcceptanceRequest.Criterion("synthetic-receipt","a".repeat(64),pointer,op,expected);}
    TaskAcceptanceRequest request(TaskAcceptanceRequest.Criterion... criteria) {return new TaskAcceptanceRequest("synthetic-request",1,"Independent synthetic verification",List.of(criteria));}
    @Test void numericAndEscapedPointerChecksDoNotCoerceStringsOrMissingValuesIntoSuccess() {
        var c=criterion("/metrics/0/p95","LE",1);policy.validate(request(c));
        assertEquals("PASSED",policy.check(c,Map.of("metrics",List.of(Map.of("p95",0.9)))).get("verdict"));
        assertEquals("FAILED",policy.check(c,Map.of("metrics",List.of(Map.of("p95",1.1)))).get("verdict"));
        assertEquals("UNKNOWN",policy.check(c,Map.of("metrics",List.of(Map.of("p95","0.9")))).get("verdict"));
        assertEquals("UNKNOWN",policy.check(c,Map.of()).get("verdict"));
        assertEquals("PASSED",policy.check(criterion("/a~1b~0c","EQ",2),Map.of("a/b~c",2.0)).get("verdict"));
    }
    @Test void noAssertionsDuplicatesAndReceiptMetadataCannotSatisfyTaskAcceptance() {
        assertThrows(IllegalArgumentException.class,()->policy.validate(request()));
        var c=criterion("/version","EQ","v1");assertThrows(IllegalArgumentException.class,()->policy.validate(request(c,c)));
        assertThrows(IllegalArgumentException.class,()->policy.check(criterion("/status","EQ","AVAILABLE"),
                Map.of("status","AVAILABLE","scope",Map.of("projectId","p"))));
        assertEquals("PASSED",policy.check(criterion("/status","EQ","HEALTHY"),Map.of("status","HEALTHY")).get("verdict"));
        assertThrows(IllegalArgumentException.class,()->policy.validate(request(criterion("/metric","GE",Double.NaN))));
        assertThrows(IllegalArgumentException.class,()->policy.validate(request(criterion("/metric","LE","100"))));
    }
    @Test void oneBusinessFieldCanHaveIndependentLowerAndUpperBounds() {
        var lower=criterion("/qps","GE",0.5);var upper=criterion("/qps","LE",2);
        assertDoesNotThrow(()->policy.validate(request(lower,upper)));
        assertEquals("PASSED",policy.check(lower,Map.of("qps",1)).get("verdict"));
        assertEquals("PASSED",policy.check(upper,Map.of("qps",1)).get("verdict"));
        assertEquals("FAILED",policy.check(lower,Map.of("qps",0.4)).get("verdict"));
        assertEquals("FAILED",policy.check(upper,Map.of("qps",2.1)).get("verdict"));
        assertThrows(IllegalArgumentException.class,()->policy.validate(request(lower,criterion("/qps","GE",0.6))));
    }
    @Test void multiWindowGoalsKeepEveryAssertionWithinAnExplicitBound() {
        var assertions=new ArrayList<TaskAcceptanceRequest.Criterion>();
        for(int i=0;i<64;i++)assertions.add(criterion("/windows/"+i+"/evidenceComplete","EQ",true));
        assertDoesNotThrow(()->policy.validate(request(assertions.toArray(TaskAcceptanceRequest.Criterion[]::new))));
        for(var c:assertions)assertEquals("FAILED",policy.check(c,Map.of("windows",java.util.stream.IntStream.range(0,64)
                .mapToObj(i->Map.of("evidenceComplete",false)).toList())).get("verdict"));
        assertions.add(criterion("/windows/64/evidenceComplete","EQ",true));
        assertThrows(IllegalArgumentException.class,()->policy.validate(request(assertions.toArray(TaskAcceptanceRequest.Criterion[]::new))));
    }
    @Test void actualCountersAndMergedHistogramAreRecomputedInsteadOfTrustingProviderVerdicts() {
        var receipt=SyntheticAcceptanceMetrics.receipt("p",0);
        assertEquals("PASSED",policy.check(criterion("/observedWindowV1/sampleCountLowerBound","GE",100),receipt).get("verdict"));
        assertEquals("PASSED",policy.check(criterion("/observedWindowV1/p95Seconds","LE",1),receipt).get("verdict"));
        assertEquals("PASSED",policy.check(criterion("/observedWindowV1/reachability","EQ","REACHABLE"),receipt).get("verdict"));
        assertEquals("PASSED",policy.check(criterion("/observedWindowV1/evidenceComplete","EQ",true),receipt).get("verdict"));
        var faulty=SyntheticAcceptanceMetrics.receipt("p",.1);
        faulty.put("observedWindowV1",Map.of("errorRate",0));
        assertEquals("FAILED",policy.check(criterion("/observedWindowV1/errorRate","LE",.01),faulty).get("verdict"));
        faulty.put("collectionDefinition","unrecognized");
        assertEquals("UNKNOWN",policy.check(criterion("/observedWindowV1/errorRate","LE",.01),faulty).get("verdict"));
    }
    @SuppressWarnings("unchecked")
    @Test void missingSamplesOrCounterResetsNeverLookLikeZeroErrorsOrLatency() {
        var receipt=SyntheticAcceptanceMetrics.receipt("p",0);
        var series=(List<Map<String,Object>>)receipt.get("series");
        var values=(List<List<Object>>)series.get(0).get("values");
        values.remove(10);values.remove(10); // A fifteen-second scrape gap.
        assertEquals("UNKNOWN",policy.check(criterion("/observedWindowV1/errorRate","LE",.01),receipt).get("verdict"));
        assertEquals("FAILED",policy.check(criterion("/observedWindowV1/evidenceComplete","EQ",true),receipt).get("verdict"));
        receipt=SyntheticAcceptanceMetrics.receipt("p",0);
        values=(List<List<Object>>)((List<Map<String,Object>>)receipt.get("series")).get(0).get("values");
        values.set(10,List.of(1050,0));
        assertEquals("UNKNOWN",policy.check(criterion("/observedWindowV1/p95Seconds","LE",1),receipt).get("verdict"));
        assertEquals("UNKNOWN",policy.check(criterion("/observedWindowV1/errorRate","LE",.01),Map.of("observedWindowV1",Map.of("errorRate",0))).get("verdict"));
    }
}
