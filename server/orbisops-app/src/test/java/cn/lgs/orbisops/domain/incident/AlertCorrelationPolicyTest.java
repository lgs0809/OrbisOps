package cn.lgs.orbisops.domain.incident;

import cn.lgs.orbisops.domain.incident.correlation.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlertCorrelationPolicyTest {
    private final AlertCorrelationPolicy policy = new AlertCorrelationPolicy();
    private final Instant now = Instant.parse("2026-09-09T03:00:00Z");
    private CorrelationSignal signal(String entity, String env, long seconds, Map<String,String> ids) {
        return new CorrelationSignal(1, "i-"+entity, "p", env, entity, now.plusSeconds(seconds), now.plusSeconds(300), ids, entity, false);
    }
    private CorrelationTopologyEdge edge(String a, String b) {
        return new CorrelationTopologyEdge(a,b,"inventory:"+a+"/"+b,now.minusSeconds(600),now.plusSeconds(600));
    }
    @Test void differentSymptomsOnDependentComponentsAreAssociatedWithoutEqualText() {
        var a = signal("queue", "test", 0, Map.of());
        var b = signal("worker", "test", 42, Map.of());
        var result = policy.compare(a,b,List.of(edge("worker","queue")),Set.of());
        assertTrue(result.join()); assertEquals(List.of("inventory:worker/queue"), result.evidenceRefs());
    }
    @Test void siblingSymptomsNeedAnAnomalousSharedNeighbourAndDoNotDependOnProductNames() {
        var a=signal("resource-x","test",0,Map.of()); var b=signal("resource-y","test",45,Map.of());
        var topology=List.of(edge("caller","resource-x"),edge("caller","resource-y"));
        assertFalse(policy.compare(a,b,topology,Set.of()).join());
        assertTrue(policy.compare(a,b,topology,Set.of("caller")).join());
    }
    @Test void sameTextTimeOrBroadServiceMembershipDoesNotMergeIndependentFailures() {
        var a=signal("orders","test",0,Map.of());
        assertFalse(policy.compare(a,signal("orders","test",0,Map.of()),List.of(),Set.of()).join());
        assertFalse(policy.compare(a,signal("billing","test",0,Map.of()),List.of(),Set.of()).join());
        assertFalse(policy.compare(a,signal("billing","prod",0,Map.of()),List.of(edge("orders","billing")),Set.of()).join());
    }
    @Test void sharedTraceDoesNotCrossProjectOrUnlimitedTimeAndExpiredTopologyCannotSupportJoining() {
        var a=signal("a","test",0,Map.of("trace_id","trace-42"));
        assertTrue(policy.compare(a,signal("b","test",100,Map.of("trace_id","trace-42")),List.of(),Set.of()).join());
        assertFalse(policy.compare(a,signal("b","test",901,Map.of("trace_id","trace-42")),List.of(),Set.of()).join());
        var foreign=new CorrelationSignal(2,"i-b","another-project","test","b",now,now,Map.of("trace_id","trace-42"),"same",false);
        assertFalse(policy.compare(a,foreign,List.of(),Set.of()).join());
        var expired=new CorrelationTopologyEdge("a","b","trace:old",now.minusSeconds(99),now.minusSeconds(1));
        assertFalse(policy.compare(a,signal("b","test",0,Map.of()),List.of(expired),Set.of()).join());
    }
    @Test void missingOrFutureOnsetAndGenericIdentitiesDoNotClaimCorrelation() {
        var a=signal("a","test",0,Map.of("trace_id","unknown"));
        assertFalse(policy.compare(a,signal("b","test",0,Map.of("trace_id","unknown")),List.of(),Set.of()).join());
        for(Instant time:Arrays.asList(null,now.plusSeconds(900))) {
            var b=new CorrelationSignal(2,"i-b","p","test","b",time,now,Map.of(),"b",false);
            assertFalse(policy.compare(a,b,List.of(edge("a","b")),Set.of()).join());
        }
    }
    @Test void hubDoesNotJoinAllItsNeighboursAndRecoveryDoesNotStartANewEpisode() {
        var edges=new ArrayList<CorrelationTopologyEdge>();
        for(int i=0;i<12;i++) edges.add(edge("hub","leaf-"+i));
        assertFalse(policy.compare(signal("leaf-1","test",0,Map.of()),signal("leaf-2","test",0,Map.of()),edges,Set.of("hub")).join());
        var recovery=new CorrelationSignal(2,"i-b","p","test","b",now,now,Map.of(),"recovered",true);
        assertFalse(policy.compare(signal("a","test",0,Map.of()),recovery,List.of(edge("a","b")),Set.of()).join());
    }
}
