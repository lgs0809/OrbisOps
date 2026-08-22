package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionFallbackFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Factory for the platform-safe fallback Agent Definition. */
public final class OpsAgentDefinitionFallbackFactory
        implements AgentDefinitionFallbackFactory<OpsAgentDefinition> {

    @Override
    public OpsAgentDefinition create() {
        return OpsAgentDefinition.builder()
                .agentId(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID)
                .version(1)
                .lifecycle("PUBLISHED")
                .name("平台通用运维 Agent")
                .engine("HYBRID")
                .description("在所选项目权限边界内，自主分析问题、读取知识库并调用项目 MCP 收集证据。")
                .instruction("""
                        你是 OrbisOps 中当前项目的默认智能助手。对用户始终以 OrbisOps 助手身份交流，不要自称底层模型、模型供应商或实现框架。
                        先理解用户真正想做什么，再按需读取 Skill、检索知识库或调用 MCP；普通寒暄、身份询问、产品使用说明和概念解释直接自然回答，不要为了显得专业而强行调用工具。
                        对“什么情况”“继续看看”“这个呢”等短追问优先结合会话历史解析指代；上下文足够时直接继续。对当前项目内宽泛但可安全探索的排查请求，先做只读探索再根据观察收窄，不要机械要求用户补齐模板化参数。
                        面向用户时先给结论或当前情况，再给必要证据和下一步；能安全做只读查询时直接查，不要先输出能力清单、规则说明或模板化免责声明。默认保持紧凑的人类可读表达，不展示内部节点、路由、任务分类和 Tool schema。
                        所有实时结论必须有 observation 支撑；证据不足时明确说明缺口，但不要把内部任务分类、路由或证据协议直接展示给用户。
                        你可以生成修复方案和风险说明，但不能绕过审批直接执行生产变更。
                        """.trim())
                .startNodeId("start")
                .defaultSubAgentMaxIterations(8)
                .queryRewriteEnabled(true)
                .nodes(List.of(
                        node("start", "START", "start", "接收项目上下文和用户问题。"),
                        OpsWorkflowNode.builder()
                                .nodeId("investigate")
                                .type("AGENT")
                                .mode("react")
                                .agent("platform-ops-react")
                                .description("自主选择当前项目允许的 Skill、RAG 和 MCP 完成调查。")
                                .instruction("按需收集证据，输出结论、证据、风险、修复建议和验证建议。")
                                .outputKey("final_answer")
                                .ragEnabled(true)
                                .changePackageEnabled(true)
                                .config(new LinkedHashMap<>(Map.of(
                                        "mode", "react",
                                        "role", "general",
                                        "inheritProjectCapabilities", true)))
                                .build(),
                        node("end", "END", "end", "返回最终分析结果。")
                ))
                .edges(List.of(
                        edge("start", "investigate", "进入自由调查。"),
                        edge("investigate", "end", "输出调查结果。")
                ))
                .build();
    }

    private OpsWorkflowNode node(String nodeId,
                                 String type,
                                 String agent,
                                 String description) {
        return OpsWorkflowNode.builder()
                .nodeId(nodeId)
                .type(type)
                .agent(agent)
                .description(description)
                .skills(List.of())
                .mcpIds(List.of())
                .config(new LinkedHashMap<>())
                .build();
    }

    private OpsGraphEdge edge(String from, String to, String description) {
        return OpsGraphEdge.builder()
                .edgeId(from + "->" + to)
                .from(from)
                .to(to)
                .conditionType("always")
                .condition("always")
                .feedback(false)
                .description(description)
                .build();
    }
}
