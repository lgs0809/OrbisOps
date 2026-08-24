package cn.lgs.orbisops.trigger.ops.runtime;

import static org.assertj.core.api.Assertions.*;
import com.alibaba.fastjson.JSON;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpsStructuredWorkflowPromptTest {
    private final Map<String, Object> schema = Map.of("type", "object", "properties",
        Map.of("status", Map.of("type", "string", "enum", List.of("READY", "NEED_INFO"))),
        "required", List.of("status"), "additionalProperties", false);
    private final Map<String, Object> contract = Map.of("format", "JSON", "schema", schema);

    @Test void internalJsonNodeDoesNotReceiveContradictoryUserAnswerProtocol() {
        var node = OpsWorkflowNode.builder().nodeId("resolve").type("AGENTSCOPE")
            .outputKey("resolvedRequest").config(Map.of("outputContract", contract)).build();
        var definition = OpsAgentDefinition.builder().nodes(List.of(node)).build();
        var config = new OpsAgentScopeConfigPolicy().configs(definition, new OpsAgentChatRequest()).get(0);
        assertThat(config.getOutputContract()).isEqualTo(contract);
        String prompt = new OpsRuntimePromptAssembler().agentInstruction(definition, config, null);
        assertThat(prompt).contains("工作流内部输出协议", "additionalProperties", "本节点 JSON Schema")
            .doesNotContain("<ops_answer>", "<ops_outcome>");
        assertThat(prompt).contains("不要要求用户填写 JSON", "OrbisOps 产品身份");
    }

    @Test void ordinaryChatStillRequiresOutcomeAndEvidenceProtocol() {
        String prompt = new OpsRuntimePromptAssembler().agentInstruction(null, new OpsAgentScopeConfig(), null);
        assertThat(prompt).contains("<ops_answer>", "<ops_outcome>", "实时诊断");
    }

    @Test void downstreamRouterRequiresTheActualSchemaRatherThanInventingAnOutputKeyField() {
        var upstream = OpsWorkflowNode.builder().nodeId("resolve").outputKey("resolvedRequest")
            .config(Map.of("outputContract", contract)).build();
        var definition = graph(upstream);
        var document = JSON.parseObject(OpsGraphRouterPromptContract.downstreamRouterContract(
            definition, upstream, edge -> true, edge -> Map.of()));
        var required = document.getJSONArray("routers").getJSONObject(0).getJSONObject("requiredOutput");
        assertThat(required.getJSONObject("properties").keySet()).containsExactly("status");
        assertThat(required.getBoolean("additionalProperties")).isFalse();
        assertThat(document.getString("rule")).contains("不需要模型另选 route key");
    }

    @Test void legacyRouterContractRemainsAvailableWithoutAnExplicitJsonSchema() {
        var upstream = OpsWorkflowNode.builder().nodeId("resolve").build();
        String prompt = OpsGraphRouterPromptContract.downstreamRouterContract(graph(upstream), upstream,
            edge -> true, edge -> Map.of());
        assertThat(prompt).contains("输出必须包含 Router 可解析的路由字段");
    }

    @Test void emptyOrTextContractsDoNotDisableOrdinaryCompletionProtocol() {
        assertThat(OpsRuntimePromptAssembler.hasJsonOutputContract(Map.of("format", "JSON", "schema", Map.of()))).isFalse();
        assertThat(OpsRuntimePromptAssembler.hasJsonOutputContract(Map.of("format", "TEXT", "schema", schema))).isFalse();
    }

    private OpsAgentDefinition graph(OpsWorkflowNode upstream) {
        var router = OpsWorkflowNode.builder().nodeId("route").type("ROUTER")
            .config(Map.of("inputKey", "resolvedRequest", "routeMode", "single")).build();
        var terminal = OpsWorkflowNode.builder().nodeId("next").type("END").build();
        return OpsAgentDefinition.builder().nodes(List.of(upstream, router, terminal)).edges(List.of(
            OpsGraphEdge.builder().from("resolve").to("route").conditionType("always").build(),
            OpsGraphEdge.builder().from("route").to("next").conditionType("expression").condition("status == READY").build())).build();
    }
}
