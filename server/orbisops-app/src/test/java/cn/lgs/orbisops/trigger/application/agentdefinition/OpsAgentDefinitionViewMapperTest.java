package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsAgentDefinitionViewMapperTest {

    @Test
    void exposesSpecializedWorkflowRoutingMetadata() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("workflow-a")
                .schemaVersion(1)
                .version(2)
                .lifecycle("PUBLISHED")
                .projectId("project-a")
                .definitionKind("SPECIALIZED_WORKFLOW")
                .workflowInvocationMode("MANUAL_ONLY")
                .workflowAutoSelectEnabled(false)
                .whenToUse(List.of("订单失败排查"))
                .whenNotToUse(List.of("普通知识问答"))
                .routingKeywords(List.of("订单失败", "下单异常"))
                .build();

        Map<String, Object> result =
                new OpsAgentDefinitionViewMapper().view(definition);

        assertEquals(1, result.get("schemaVersion"));
        assertEquals("SPECIALIZED_WORKFLOW", result.get("definitionKind"));
        assertEquals("MANUAL_ONLY", result.get("workflowInvocationMode"));
        assertEquals(false, result.get("workflowAutoSelectEnabled"));
        assertEquals(List.of("订单失败排查"), result.get("whenToUse"));
        assertEquals(List.of("普通知识问答"), result.get("whenNotToUse"));
        assertEquals(List.of("订单失败", "下单异常"), result.get("routingKeywords"));
    }
}
