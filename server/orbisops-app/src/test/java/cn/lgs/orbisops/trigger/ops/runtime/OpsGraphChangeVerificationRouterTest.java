package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsGraphChangeVerificationRouterTest {
    final Instant now=Instant.parse("2026-09-09T03:00:00Z");
    final ChangePackageQueryService changes=mock(ChangePackageQueryService.class);
    final OpsWorkflowChangeContextReader reader=new OpsWorkflowChangeContextReader(changes);
    final OpsGraphRouterNodeExecutor router=new OpsGraphRouterNodeExecutor(Clock.fixed(now,ZoneOffset.UTC),reader);

    @Test void claimedLandingAndRelaxedSloCannotReplaceRepositoryState() {
        when(changes.detail("cp")).thenReturn(Map.of("packageId","cp","projectId","p","status","APPROVED"));
        var result=router.execute(context("CONTEXT_CHANGE","context",Map.of(),Map.of("projectId","p","packageId","cp",
                "status","LANDED","maxP95Seconds",100,"approvedPackageHash","claimed")));
        var value=(Map<?,?>)result.result().get("workflowData_context");
        assertEquals(false,value.get("readyToCollect"));
        assertEquals("INCONCLUSIVE",value.get("status"));
        assertFalse(value.containsKey("maxP95Seconds"));
        verify(changes).detail("cp");
        verifyNoMoreInteractions(changes);
    }
    @Test void missingRecordProducesExplicitGapAndReportWithNoSuccessClaim() {
        when(changes.detail("missing")).thenThrow(new IllegalArgumentException("not found"));
        var value=reader.read("p",Map.of("projectId","p","packageId","missing"),now);
        var result=router.execute(context("REPORT_CHANGE","report",Map.of("workflowData_context",value),Map.of()));
        assertEquals(1,result.result().size());
        assertEquals("INCONCLUSIVE",((Map<?,?>)result.result().get("workflowData_report")).get("status"));
        assertTrue(result.output().contains("CHANGE_RECORD_UNAVAILABLE"));
        assertFalse(result.output().contains("PASS"));
    }
    @Test void foreignProjectIsRejectedBeforeReadingAndReturnedForeignPackageIsAlsoRejected() {
        assertThrows(SecurityException.class,()->reader.read("p",Map.of("projectId","other","packageId","cp"),now));
        verifyNoInteractions(changes);
        when(changes.detail("cp")).thenReturn(Map.of("projectId","other","packageId","cp","status","LANDED"));
        assertThrows(SecurityException.class,()->reader.read("p",Map.of("projectId","p","packageId","cp"),now));
        verify(changes).detail("cp");
        verifyNoMoreInteractions(changes);
    }
    @Test void userCannotSupplyContextToBypassAnUnavailableRepository() {
        var executor=new OpsGraphRouterNodeExecutor(Clock.fixed(now,ZoneOffset.UTC));
        assertThrows(IllegalStateException.class,()->executor.execute(context("CONTEXT_CHANGE","context",
                Map.of("workflowData_context",Map.of("readyToCollect",true)),Map.of("projectId","p","packageId","cp"))));
    }
    @Test void selectedIdentityIsResolvedWithoutTrustingClaimsOrCrossingProjectScope() {
        assertEquals(Map.of("status","NEED_RESOLUTION"),reader.selected("p",Map.of()));verifyNoInteractions(changes);
        when(changes.detail("cp")).thenReturn(Map.of("packageId","cp","projectId","p","status","APPROVED"));
        assertEquals(Map.of("status","READY","projectId","p","packageId","cp"),reader.selected("p",Map.of(
                "selectedChangePackageId","cp","status","LANDED","approvedVersion",999,"maxP95Seconds",99)));
        when(changes.detail("foreign")).thenReturn(Map.of("packageId","foreign","projectId","other"));
        assertThrows(SecurityException.class,()->reader.selected("p",Map.of("selectedChangePackageId","foreign")));
        assertThrows(IllegalArgumentException.class,()->reader.selected("p",Map.of("selectedChangePackageId",Map.of("packageId","cp"))));
        when(changes.detail("missing")).thenThrow(new IllegalArgumentException("missing"));
        assertThrows(IllegalArgumentException.class,()->reader.selected("p",Map.of("selectedChangePackageId","missing")));
    }
    @Test void selectedInputNodeReturnsOnlyTheRepositoryCheckedIdentity() {
        var context=context("SELECT_CHANGE_REQUEST","selection",Map.of(),Map.of());
        context.request().setMetadata(Map.of("selectedChangePackageId","cp"));
        when(changes.detail("cp")).thenReturn(Map.of("packageId","cp","projectId","p","status","LANDED"));
        var value=(Map<?,?>)router.execute(context).result().get("workflowData_selection");
        assertEquals(Map.of("status","READY","projectId","p","packageId","cp"),value);
        verify(changes).detail("cp");verifyNoMoreInteractions(changes);
    }
    @Test void selectedJsonSurvivesLifecycleOutputProjectionAndFeedsRepositoryContext() {
        when(changes.detail("cp")).thenReturn(Map.of("packageId","cp","projectId","p","status","APPROVED"));
        var selection=context("SELECT_CHANGE_REQUEST","selection",Map.of(),Map.of());
        selection.request().setMetadata(Map.of("selectedChangePackageId","cp"));
        var selected=router.execute(selection).result();
        var projection=context("SELECT_CHANGE_REQUEST","selection",selected,Map.of());
        projection.node().setOutputKey("resolvedRequest");
        projection.node().setConfig(Map.of("inputKey","workflowData_selection","inputFormat","JSON"));
        var projected=router.execute(projection);
        assertEquals(Map.of("status","READY","projectId","p","packageId","cp"),CanonicalJson.parseObject(projected.output()));
        // OpsGraphNodeLifecycle stores execution.output() under the explicit outputKey.
        var downstream=context("CONTEXT_CHANGE","context",Map.of("resolvedRequest",projected.output()),Map.of());
        downstream.node().setConfig(Map.of("requestInputKey","resolvedRequest","changeVerification",
                Map.of("operation","CONTEXT_CHANGE","outputKey","context")));
        var result=(Map<?,?>)router.execute(downstream).result().get("workflowData_context");
        assertEquals("INCONCLUSIVE",result.get("status"));
        assertEquals(false,result.get("readyToCollect"));
        verify(changes,times(2)).detail("cp");
    }
    private OpsGraphNodeExecutionContext context(String operation,String output,Map<String,Object> state,Map<String,Object> input) {
        var config=java.util.Set.of("CONTEXT_CHANGE","SELECT_CHANGE_REQUEST").contains(operation) ? Map.of("operation",operation,"outputKey",output)
                : Map.of("operation",operation,"outputKey",output,"contextKey","context");
        var node=OpsWorkflowNode.builder().nodeId("rules").type("ROUTER").config(Map.of("changeVerification",config)).build();
        String query=CanonicalJson.stringifyPreservingOrder(input);
        return new OpsGraphNodeExecutionContext(OpsAgentDefinition.builder().agentId("workflow-c").build(),node,
                OpsAgentChatRequest.builder().query(query).projectId("p").runId("run").build(),new OverAllState(state),
                new ArrayList<>(),null,null,"ROUTER","",null,query,"",0,0,false);
    }
}
