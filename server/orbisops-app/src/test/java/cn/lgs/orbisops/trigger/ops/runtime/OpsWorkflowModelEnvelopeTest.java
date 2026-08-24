package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reproduces the extra outputKey envelope observed in a real Luna resolver response. */
class OpsWorkflowModelEnvelopeTest {
    private final String body = "{\"status\":\"READY\",\"projectId\":\"project\",\"environment\":\"acceptance\",\"serviceId\":\"service-3\"}";
    private OpsWorkflowNode node() {
        return OpsWorkflowNode.builder().nodeId("resolve_request").type("AGENT").mode("REACT")
                .outputKey("resolvedRequest").config(Map.of("outputContract",Map.of("format","JSON","schema",Map.of(
                        "type","object","additionalProperties",false,"required",List.of("status","projectId","environment","serviceId"),
                        "properties",Map.of("status",Map.of("enum",List.of("READY")),"projectId",Map.of("type","string"),
                                "environment",Map.of("type","string"),"serviceId",Map.of("type","string")))))).build();
    }
    private BoundWorkflowExecutionPlan plan() {
        return new BoundWorkflowExecutionPlan(1,1,"definition","agent","session","run","project","bundle","context",
                "resolve_request",List.of(new BoundWorkflowNode("resolve_request","AGENT","REACT","agent","config",List.of())),
                List.of(),List.of(),"plan",Instant.now(),List.of());
    }
    @Test void exactSingleWrapperIsNormalizedBeforeGraphVariablesAndCheckpointValidation() {
        var node=node();var state=mock(OpsAnalysisRuntimeStateManager.class);
        var lifecycle=new OpsGraphNodeLifecycle(new OpsGraphRuntimeStateManager(),mock(OpsGraphTopologyAssembler.class),state,
                new OpsAnalysisRoutingPolicy(),new OpsGraphNodeLifecycleReporter(state,null,null));
        var hooks=mock(OpsGraphNodeExecutionCoordinator.Hooks.class);when(hooks.executionNodeType(node)).thenReturn("AGENTSCOPE");
        var events=new ArrayList<OpsRuntimeEvent>();
        var result=lifecycle.execute(OpsAgentDefinition.builder().agentId("agent").build(),node,
                OpsAgentChatRequest.builder().runId("run").query("只读巡检服务3").metadata(new HashMap<>()).build(),
                new OverAllState(Map.of()),events,null,hooks,
                context -> new OpsGraphNodeExecutionResult("{\"resolvedRequest\":"+body+"}",Map.of()));
        assertEquals(body,result.get("output"));assertEquals(body,result.get("resolvedRequest"));
        assertTrue(events.stream().anyMatch(e->"NODE_OUTPUT_NORMALIZED".equals(e.getEventType())));
        assertDoesNotThrow(()->OpsWorkflowNodeOutputContract.compile(node).validate(plan(),"resolve_request",result));
    }
    @Test void wrongAmbiguousIncompleteAndDuplicateWrappersRemainRejected() {
        var contract=OpsWorkflowNodeOutputContract.compile(node());
        for(String bad:List.of("{\"wrong\":"+body+"}","{\"resolvedRequest\":"+body+",\"extra\":true}",
                "{\"resolvedRequest\":{\"status\":\"READY\"}}",
                "{\"resolvedRequest\":{},\"resolvedRequest\":"+body+"}","{\"resolvedRequest\":"+body+"} {}")) {
            assertEquals(bad,contract.normalize(bad,"resolvedRequest"));
            assertThrows(RuntimeException.class,()->contract.validate(plan(),"resolve_request",Map.of("output",bad)));
        }
    }
    @Test void normalizationDoesNotBypassProjectIdentityOrBusinessRuleChecks() {
        var node=node();var contract=OpsWorkflowNodeOutputContract.compile(node);
        String other=contract.normalize("{\"resolvedRequest\":"+body.replace("\"project\"","\"other-project\"")+"}","resolvedRequest");
        assertEquals("WORKFLOW_NODE_OUTPUT_IDENTITY_MISMATCH:projectId",
                assertThrows(RuntimeException.class,()->contract.validate(plan(),"resolve_request",Map.of("output",other))).getMessage());
        var config=new HashMap<>(node.getConfig());
        var output=new HashMap<>((Map<String,Object>)config.get("outputContract"));
        output.put("rule","nodeOutput.serviceId == 'allowed-service'");config.put("outputContract",output);node.setConfig(config);
        var restricted=OpsWorkflowNodeOutputContract.compile(node);
        String normalized=restricted.normalize("{\"resolvedRequest\":"+body+"}","resolvedRequest");
        assertEquals("WORKFLOW_NODE_OUTPUT_BUSINESS_RULE_REJECTED",
                assertThrows(RuntimeException.class,()->restricted.validate(plan(),"resolve_request",Map.of("output",normalized))).getMessage());
    }
    @Test void validOriginalObjectAndContractThatIntendsAnEnvelopeAreUnchanged() {
        var contract=OpsWorkflowNodeOutputContract.compile(node());assertEquals(body,contract.normalize(body,"resolvedRequest"));
        var node=node();node.setConfig(Map.of("outputContract",Map.of("format","JSON","schema",Map.of(
                "type","object","required",List.of("resolvedRequest")))));
        String wrapped="{\"resolvedRequest\":"+body+"}";
        assertEquals(wrapped,OpsWorkflowNodeOutputContract.compile(node).normalize(wrapped,"resolvedRequest"));
    }
}
