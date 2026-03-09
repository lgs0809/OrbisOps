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
                        你是通用运维 Agent。必须在当前项目的资源、知识库、Skill 和权限边界内工作。
                        先理解问题并形成调查思路，再按需读取 Skill、检索知识库或调用 MCP 工具。
                        所有实时结论必须有 observation 支撑；证据不足时明确说明缺口。
                        你可以生成修复方案和风险说明，但不能绕过审批直接执行变更。
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
