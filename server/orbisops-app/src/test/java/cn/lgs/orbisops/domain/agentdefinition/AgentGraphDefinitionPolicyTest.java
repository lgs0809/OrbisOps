package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentGraphDefinitionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentGraphDefinitionPolicyTest {

    private final AgentGraphDefinitionPolicy policy = new AgentGraphDefinitionPolicy();

    @Test
    void acceptsOneReachableAndConvergentGraph() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        node("worker", "AGENT", "worker", true, List.of(), Map.of()),
                        node("end", "END", "platform", false, List.of(), Map.of())),
                List.of(
                        edge("start", "worker", "always", false),
                        edge("worker", "end", "always", false)),
                List.of());

        assertDoesNotThrow(() -> policy.validate(graph));
    }

    @Test
    void rejectsDuplicateNodeIdentity() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        node("start", "END", "platform", false, List.of(), Map.of())),
                List.of(),
                List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(graph));

        assertEquals("节点 ID 重复：start", error.getMessage());
    }

    @Test
    void reactNodeRequiresAnExplicitRuntimeCapability() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        new AgentGraphDefinition.Node(
                                "worker", "AGENT", "REACT", "worker",
                                false, false, List.of(), 0, Map.of()),
                        node("end", "END", "platform", false, List.of(), Map.of())),
                List.of(
                        edge("start", "worker", "always", false),
                        edge("worker", "end", "always", false)),
                List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(graph));

        assertEquals(
                "节点 worker 是 ReAct/工具执行节点，必须显式选择 MCP、启用 RAG 或配置受支持的只读变更查询能力。"
                        + "不能通过节点名称或路由条件推断工具能力",
                error.getMessage());
    }

    @Test
    void routeConditionMustOriginateFromRouterNode() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        node("worker", "AGENT", "worker", true, List.of(), Map.of()),
                        node("end", "END", "platform", false, List.of(), Map.of())),
                List.of(
                        edge("start", "worker", "always", false),
                        edge("worker", "end", "route_match", false)),
                List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(graph));

        assertEquals("路由条件边必须从 Router 节点发出：worker -> end", error.getMessage());
    }

    @Test
    void everyNodeMustBeReachableAndConvergeToEnd() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        node("worker", "AGENT", "worker", true, List.of(), Map.of()),
                        node("orphan", "AGENT", "worker", true, List.of(), Map.of()),
                        node("end", "END", "platform", false, List.of(), Map.of())),
                List.of(
                        edge("start", "worker", "always", false),
                        edge("worker", "end", "always", false),
                        edge("orphan", "end", "always", false)),
                List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(graph));

        assertEquals("存在无法从 START 到达的节点：orphan", error.getMessage());
    }

    @Test
    void loopRoundsAreBounded() {
        AgentGraphDefinition graph = graph(
                List.of(
                        node("start", "START", "platform", false, List.of(), Map.of()),
                        node("router", "ROUTER", "router", false, List.of(), Map.of()),
                        node("worker", "AGENT", "worker", true, List.of(), Map.of()),
                        node("end", "END", "platform", false, List.of(), Map.of())),
                List.of(
                        edge("start", "router", "always", false),
                        edge("router", "worker", "route_match", false),
                        edge("worker", "router", "always", false),
                        edge("router", "end", "default", false)),
                List.of(new AgentGraphDefinition.Loop(
                        "main-loop",
                        List.of("router", "worker"),
                        List.of("router->worker"),
                        21,
                        "router->end")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(graph));

        assertEquals("循环策略 main-loop maxRounds 必须在 1-20 之间", error.getMessage());
    }

    private AgentGraphDefinition graph(
            List<AgentGraphDefinition.Node> nodes,
            List<AgentGraphDefinition.Edge> edges,
            List<AgentGraphDefinition.Loop> loops) {
        return new AgentGraphDefinition(
                "agent-1",
                "GRAPH",
                "start",
                nodes,
                edges,
                loops);
    }

    private AgentGraphDefinition.Node node(
            String id,
            String type,
            String agent,
            boolean ragEnabled,
            List<String> mcpIds,
            Map<String, Object> config) {
        return new AgentGraphDefinition.Node(
                id,
                type,
                "",
                agent,
                ragEnabled,
                false,
                mcpIds,
                0,
                config);
    }

    private AgentGraphDefinition.Edge edge(
            String from,
            String to,
            String condition,
            boolean feedback) {
        return new AgentGraphDefinition.Edge(from, to, condition, feedback);
    }
}
