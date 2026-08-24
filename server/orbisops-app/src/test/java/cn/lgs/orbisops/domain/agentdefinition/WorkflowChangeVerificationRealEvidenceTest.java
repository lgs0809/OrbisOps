package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real Prometheus and resource data, synthetic comparison context: NOT an approved release test. */
@EnabledIfSystemProperty(named="ops04.change.evidence",matches=".+")
class WorkflowChangeVerificationRealEvidenceTest {
    @Test void realCompletedRequestWindowsRespectAbsoluteSloAndSampleFloor() throws Exception {
        Path source=Path.of(System.getProperty("ops04.change.evidence"));
        var evidence=CanonicalJson.parseObject(Files.readString(source));
        assertEquals("PASS",evidence.get("result"));
        var reports=new ArrayList<Map<String,Object>>();
        for (Object raw:(List<?>)evidence.get("samples")) {
            var sample=map(raw); var beforeWindow=map(sample.get("beforeWindow"));
            var actualVersion=map(sample.get("version"));
            var criteria=new LinkedHashMap<String,Object>(Map.ofEntries(
                    Map.entry("serviceId",beforeWindow.get("serviceId")),Map.entry("environment",beforeWindow.get("environment")),
                    Map.entry("baselineVersion",actualVersion.get("version")),Map.entry("expectedVersion",actualVersion.get("version")),
                    Map.entry("changeKind","RELEASE"),Map.entry("resourceIdentity",actualVersion.get("resourceIdentity")),
                    Map.entry("routeDefinition",actualVersion.get("routeDefinition")),Map.entry("collectionDefinition",actualVersion.get("collectionDefinition")),
                    Map.entry("maxErrorRate",.01),Map.entry("maxP95Seconds",1.),Map.entry("minQps",0.),Map.entry("maxQps",2.)));
            // Only the arithmetic policy is exercised here. CONTEXT_CHANGE's repository
            // and approval gate are separately tested and are not bypassed in the business graph.
            var context=Map.<String,Object>of("projectId",beforeWindow.get("projectId"),"readyToCollect",true,"extensionCount",0,
                    "beforeWindow",beforeWindow,"afterWindow",sample.get("afterWindow"),"criteria",criteria,
                    "changeRef",Map.of("packageId","RULE_COMPONENT_ONLY_NO_CHANGE_OCCURRED"));
            var report=new WorkflowChangeVerificationPolicy().review(context,map(sample.get("before")),map(sample.get("after")),actualVersion);
            int service=((Number)sample.get("service")).intValue();
            String expected=switch(service) { case 1 -> "PASS"; case 2,4 -> "FAIL"; default -> "INCONCLUSIVE"; };
            assertEquals(expected,report.get("status"),()->CanonicalJson.stringifyPreservingOrder(report));
            if (service==2) assertTrue(((List<?>)report.get("failedChecks")).contains("POST_ERROR_SLO_EXCEEDED"));
            if (service==4) assertTrue(((List<?>)report.get("failedChecks")).contains("POST_LATENCY_SLO_EXCEEDED"));
            if (service==3) assertEquals(true,report.get("needsExtension"));
            for (String stage:List.of("before","after")) {
                var db=map(map(sample.get("database")).get(stage));
                var metrics=map(map(report.get(stage)).get("metrics"));
                assertTrue(((Number)db.get("samples")).doubleValue()>=((Number)metrics.get("sampleCountLowerBound")).doubleValue());
                if (service==1) assertTrue(((Number)db.get("errorRate")).doubleValue()<=.01 && ((Number)db.get("p95Seconds")).doubleValue()<=1.);
                if (service==4) assertTrue(((Number)db.get("p95Seconds")).doubleValue()>1.);
                if (service==2) assertTrue(((Number)db.get("errorRate")).doubleValue()>.01);
                if (service==3) assertTrue(((Number)db.get("samples")).intValue()<100);
            }
            reports.add(Map.of("service",service,"report",report,"run",sample.get("run")));
        }
        assertEquals(4,reports.size());
        Files.writeString(source.resolveSibling("c-real-policy-results.json"),CanonicalJson.stringifyPreservingOrder(Map.of(
                "result","PASS","scope","real component arithmetic; synthetic comparison context; no approved Landing or release", "reports",reports)));
    }
    @SuppressWarnings("unchecked") Map<String,Object> map(Object value) { return (Map<String,Object>)value; }
}
