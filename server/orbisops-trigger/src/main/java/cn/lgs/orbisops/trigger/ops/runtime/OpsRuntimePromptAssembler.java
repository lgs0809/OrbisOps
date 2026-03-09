package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds runtime instructions and projects an explicitly bounded subset of graph state into node prompts.
 * Spring Graph state remains an outer-layer type and never crosses into Application or Domain.
 */
final class OpsRuntimePromptAssembler {

    private static final String LOOP_ROUNDS_KEY = "loopRounds";

    String systemPrompt(OpsAgentDefinition definition,
                        OpsWorkflowNode node,
                        OpsRuntimeResourceBundle bundle) {
        String instruction = composeRuntimeInstruction(
                globalInstructionForNode(definition, node),
                node == null ? null : node.getInstruction(),
                "节点 System Prompt",
                "你是一个生产运维 AI Agent。必须按证据回答，区分事实、推断和缺口。");
        if (bundle != null && StringUtils.hasText(bundle.getSkillContext())) {
            instruction += "\n\n### 可用 Skill 与使用边界\n" + bundle.getSkillContext();
        }
        return instruction;
    }

    String agentInstruction(OpsAgentDefinition definition,
                            OpsAgentScopeConfig config,
                            OpsRuntimeResourceBundle bundle) {
        String instruction = composeRuntimeInstruction(
                definition == null ? null : definition.getInstruction(),
                config == null ? null : config.getInstruction(),
                "子 Agent System Prompt",
                "你是一个生产运维 AI 子 Agent。");
        if (bundle != null && StringUtils.hasText(bundle.getSkillContext())) {
            instruction += "\n\n### 可用 Skill 与使用边界\n" + bundle.getSkillContext();
        }
        instruction += resourceIdentityResolutionContract(bundle);
        instruction += reactOutcomeContract();
        return instruction;
    }

    private String resourceIdentityResolutionContract(OpsRuntimeResourceBundle bundle) {
        if (bundle == null || bundle.getMcpServers() == null) return "";
        boolean openApiAvailable = bundle.getMcpServers().stream()
                .filter(server -> server != null)
                .anyMatch(server -> (server.getAllowedTools() != null
                        && server.getAllowedTools().stream()
                        .anyMatch("openapi_list_operations"::equalsIgnoreCase))
                        || text(server.getMcpId()).toLowerCase(Locale.ROOT).contains("openapi")
                        || text(server.getName()).toLowerCase(Locale.ROOT).contains("openapi"));
        if (!openApiAvailable) return "";
        return """

                ### 业务资源身份解析约束
                - 当前项目提供只读 OpenAPI/Swagger MCP。用户只给业务名称、业务动作或自然语言对象而没有明确 URI/API path 时，必须先使用该项目 OpenAPI MCP 获取权威操作目录并解析精确资源身份，再进行依赖 URI 的指标、日志或其他实时查询。
                - 在权威解析完成前，不得根据业务词自行猜测、拼接、模糊匹配或正则扩展 URI/API path；不得用 `.*业务词.*`、相似英文名或历史记忆中的其他接口替代当前业务对象。
                - 若 OpenAPI 中没有唯一匹配，保留歧义并明确资源身份不足；不要伪造 endpoint。用户已明确给出精确 URI，或本轮已有权威工具结果明确解析 URI 时，不必重复解析。
                """;
    }

    private String reactOutcomeContract() {
        return """

                ### ReAct 证据与最终结果协议
                - 实时诊断、故障复核、恢复验证、变更准备必须优先使用对应的权威实时数据源工具；用户声称“已确认”只能作为上下文，不能替代本轮实时证据。
                - 若用户要求生成 ChangePackage：本调查阶段只负责先收集足以支撑候选方案的实时证据与权威资源身份；不要在调查阶段创建变更包。调查结束后由平台受控 PREPARE governance 独立决定并创建可审核提案，绝不直接执行生产变更或绕过审批。
                - 若用户要求判断是否恢复/RESOLVED：必须使用权威实时数据源验证；没有验证证据时结论必须是 INSUFFICIENT，不能宣布恢复。
                - 在选择任何 Skill/RAG/实时数据源/变更工具之前，先做 authority preflight。若用户请求绕过审批、越权写入、直接执行生产重启/配置修改/SQL 变更等生产副作用，本轮必须立即拒绝该越权动作：不要调用 Skill、RAG、Prometheus、Elasticsearch、MySQL 或任何写工具去判断“是否值得执行”，不要创建 Incident/ChangePackage，actionClassification=ABSTAIN、requiresAction=false、abstained=true。只有用户改为请求合规的只读诊断或可审核 PREPARE 提案后，才进入工具调查。
                - Skill 和知识库是按需辅助，不是实时事实源；存在直接实时数据源时，不要用 Skill/RAG 代替实时查询。实时查询返回空结果时也不要为了“补齐背景”自动调用 RAG；空结果应保留为观测缺口，除非用户明确要求 SOP/稳定知识，或已有一个具体知识缺口阻塞下一步。
                - OpenAPI/Swagger、Tool Catalog、Skill 加载只解决“有哪些能力/要查哪个资源”，属于发现与资源身份证据，不等于“当前线上发生了什么”的实时事实证据。对于实时诊断、恢复验证或受控处置请求，只要存在适用且允许调用的实时事实源，就不得在零次实时事实查询的情况下仅凭发现结果直接输出 INSUFFICIENT；至少实际查询一个与当前问题直接相关的实时事实源，失败/空结果也应作为本轮观测记录。
                - 实时数据源遵循最小充分证据原则：普通只读诊断不要仅因为当前数据源返回空结果、无序列或零命中就无目的扩源。只有用户明确要求额外维度、当前事实提出了需要另一来源验证的具体假设，或本轮目标是正式 Incident/Remediation/ChangePackage 且结论需要独立证据交叉支撑时，才增加另一类实时事实源。
                - 正式 Incident/Remediation/ChangePackage 的证据门槛高于普通问答：当运行时同时提供多个与故障直接相关、相互独立的实时事实来源（例如指标状态与错误日志），应在可用预算内取得交叉证据后再认定证据充分并进入正式处置链。某一来源不可用、被策略阻断或查询失败时要如实降级为 PARTIAL/INSUFFICIENT，不能伪造第二来源，也不能把 OpenAPI/Tool Catalog 当作第二份故障证据。
                - 最终回答正文之后必须追加且只追加一个机器可读 outcome；该块不会展示给用户。严格按以下五行 key=value 输出，五个字段均不可省略，不要加 Markdown：
                <ops_outcome>
                actionClassification=DIAGNOSE|REMEDIATE|VERIFY|ABSTAIN
                requiresAction=true|false
                verificationStatus=NOT_APPLICABLE|SUCCEEDED|FAILED|INSUFFICIENT
                abstained=true|false
                evidenceCompleteness=COMPLETE|PARTIAL|INSUFFICIENT
                </ops_outcome>
                - 每个带竖线的值都必须根据本轮真实语义选择其中一个实际值，不能原样输出候选，也没有默认值。
                - actionClassification 的边界：DIAGNOSE=只读调查/解释，本轮之后没有被明确要求进入受控处置链；REMEDIATE=用户明确要求 Incident 归档/关联、ChangePackage、受控修复、回滚或其他需要后续处置的动作，即使本轮只做证据收集或 PREPARE、绝不立即执行生产副作用；VERIFY=纯验证当前是否恢复/是否满足验证条件；ABSTAIN=因越权、禁止的生产副作用或其他安全边界而拒绝执行请求。
                - requiresAction 表示本轮回答之后是否必须进入受控的 Incident/Remediation/ChangePackage/人工处置链，而不是“回答中是否存在可选建议”或“本轮是否立即执行生产动作”。当 actionClassification=REMEDIATE 时必须 requiresAction=true；当 actionClassification=ABSTAIN 时必须 requiresAction=false。只要用户明确要求创建/归档/关联 Incident、生成或审核 ChangePackage、执行合规 Remediation/回滚等受控后续，即使当前实时证据是 PARTIAL/INSUFFICIENT，也必须 actionClassification=REMEDIATE 且 requiresAction=true；证据不足只影响 evidenceCompleteness 和结论强度，不取消显式后续请求。普通只读查询或诊断，即使观测到异常、证据不足、建议继续观察/排查，也保持 actionClassification=DIAGNOSE 且 requiresAction=false。纯 VERIFY 请求只负责判定验证结果，保持 actionClassification=VERIFY 且 requiresAction=false，除非用户同时明确要求处置，此时按 REMEDIATE 处理。若请求本身要求绕过审批/越权生产副作用并被拒绝，则 actionClassification=ABSTAIN、requiresAction=false、abstained=true。
                - evidenceCompleteness 只评价已获得的真实证据；没有任何权威事实证据时不得填 COMPLETE。
                """;
    }

    String buildNodePrompt(OpsAgentDefinition definition,
                           OpsWorkflowNode node,
                           String originalQuery,
                           String input,
                           OverAllState state,
                           ContextPolicy policy) {
        if (node == null) throw new IllegalArgumentException("RUNTIME_PROMPT_NODE_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("RUNTIME_PROMPT_CONTEXT_POLICY_REQUIRED");
        String edgeContext = OpsGraphEdgePromptContext.nodePromptContext(
                definition, node, edge -> policy.edgeActive(edge, state));
        String downstreamRouterContract = OpsGraphEdgePromptContext.downstreamRouterContract(
                definition, node,
                edge -> policy.edgeActive(edge, state),
                edge -> policy.edgeRuntimeMetadata(definition, state, edge));
        String contextInputSection = selectedContextInputSection(
                node, originalQuery, input, state, policy);
        return """
                ### 原始用户问题
                %s

                %s

                %s

                %s

                ### 当前节点
                nodeId: %s
                type: %s
                agent: %s
                routeKey: %s
                mcpIds: %s
                description: %s

                请执行当前节点职责，并输出可被下游节点继续使用的结论、证据和缺口。
                """.formatted(
                text(originalQuery),
                contextInputSection,
                StringUtils.hasText(edgeContext) ? edgeContext : "### 入边交接上下文\n无显式边交接配置。",
                StringUtils.hasText(downstreamRouterContract)
                        ? "### 下游 Router 输出契约\n" + downstreamRouterContract
                        + "\n\n请让当前节点输出满足上述 Router inputKey 和 route key 要求。"
                        : "### 下游 Router 输出契约\n无。",
                text(node.getNodeId()),
                policy.executionNodeType(node),
                text(node.getAgent()),
                text(policy.incomingRouteKey(definition, node)),
                node.getMcpIds() == null ? List.of() : node.getMcpIds(),
                text(node.getDescription()));
    }

    String globalInstructionForNode(OpsAgentDefinition definition, OpsWorkflowNode node) {
        if (definition == null || !nodeInheritsGlobalSystemPrompt(node)) return null;
        return definition.getInstruction();
    }

    private boolean nodeInheritsGlobalSystemPrompt(OpsWorkflowNode node) {
        if (node == null) return true;
        String configured = firstText(configText(node, "globalPromptMode"),
                configText(node, "systemPromptMode"));
        if (!StringUtils.hasText(configured)) return true;
        String normalized = configured.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return !Set.of("NODE_ONLY", "LOCAL_ONLY", "ISOLATED", "NONE", "DISABLED", "OFF")
                .contains(normalized);
    }

    private String composeRuntimeInstruction(String globalInstruction,
                                             String localInstruction,
                                             String localTitle,
                                             String fallbackInstruction) {
        String global = StringUtils.hasText(globalInstruction) ? globalInstruction.trim() : null;
        String local = StringUtils.hasText(localInstruction) ? localInstruction.trim() : null;
        if (global == null && local == null) return fallbackInstruction;
        if (global == null) return local;
        if (local == null || global.equals(local)) return global;
        String title = StringUtils.hasText(localTitle) ? localTitle.trim() : "节点 System Prompt";
        return "### 全局 System Prompt\n" + global + "\n\n### " + title + "\n" + local;
    }

    private String selectedContextInputSection(OpsWorkflowNode node,
                                               String currentQuery,
                                               String upstreamOutput,
                                               OverAllState state,
                                               ContextPolicy policy) {
        List<String> keys = contextInputKeys(node);
        if (keys.isEmpty()) keys = defaultContextInputKeys(node, policy);
        StringBuilder section = new StringBuilder("### 节点可见上下文\n");
        for (String key : keys) {
            if (!StringUtils.hasText(key)) continue;
            Object value = resolveContextInput(key.trim(), currentQuery, upstreamOutput, state);
            if (isEmptyContextValue(value)) continue;
            section.append("- ").append(key.trim()).append(":\n")
                    .append(formatContextInputValue(value, 1800))
                    .append("\n");
        }
        if ("### 节点可见上下文\n".contentEquals(section)) {
            section.append("- upstreamOutputs:\n")
                    .append(formatContextInputValue(upstreamOutput, 1800))
                    .append("\n");
        }
        return section.toString().trim();
    }

    private List<String> defaultContextInputKeys(OpsWorkflowNode node, ContextPolicy policy) {
        String type = policy.executionNodeType(node);
        String role = policy.analysisAgentRole(node);
        String nodeId = node == null ? "" : text(node.getNodeId()).toLowerCase(Locale.ROOT);
        String agent = node == null ? "" : text(node.getAgent()).toLowerCase(Locale.ROOT);
        if ("REPORT".equals(type)
                || "reporter".equals(role)
                || nodeId.contains("report")
                || agent.contains("report")) {
            return List.of("query", "results", "upstreamOutputs");
        }
        return List.of("query", "upstreamOutputs");
    }

    private List<String> contextInputKeys(OpsWorkflowNode node) {
        Object configured = Optional.ofNullable(node)
                .map(OpsWorkflowNode::getConfig)
                .map(config -> config.get("contextInputs"))
                .orElse(null);
        if (configured instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        if (configured instanceof Object[] values) {
            return java.util.Arrays.stream(values)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        if (configured instanceof String value) {
            return java.util.Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private Object resolveContextInput(String key,
                                       String currentQuery,
                                       String upstreamOutput,
                                       OverAllState state) {
        String normalized = key.trim();
        String stateKey = normalized.startsWith("state.")
                ? normalized.substring("state.".length()) : normalized;
        return switch (stateKey) {
            case "query" -> stateValue(state, "query", currentQuery);
            case "originalQuery" -> stateValue(state, "originalQuery", currentQuery);
            case "rewrittenQuery" -> stateValue(state, "rewrittenQuery", currentQuery);
            case "memoryContext" -> stateValue(state, "memoryContext", "");
            case "upstreamOutput", "upstreamOutputs", "previousOutput", "output" -> upstreamOutput;
            case "loopState" -> stateValue(state, LOOP_ROUNDS_KEY, Map.of());
            default -> {
                Object direct = stateValue(state, normalized, null);
                if (direct != null) yield direct;
                Object stripped = stateValue(state, stateKey, null);
                if (stripped != null) yield stripped;
                yield nestedStateValue(state, stateKey);
            }
        };
    }

    private Object stateValue(OverAllState state, String key, Object fallback) {
        if (state == null || !StringUtils.hasText(key)) return fallback;
        return state.value(key).orElse(fallback);
    }

    private Object nestedStateValue(OverAllState state, String key) {
        if (state == null || !StringUtils.hasText(key) || !key.contains(".")) return null;
        Object current = state.data();
        for (String segment : key.split("\\.")) {
            if (!StringUtils.hasText(segment)) return null;
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) return null;
            current = map.get(segment);
        }
        return current;
    }

    private boolean isEmptyContextValue(Object value) {
        if (value == null) return true;
        if (value instanceof String string) return !StringUtils.hasText(string);
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return false;
    }

    private String formatContextInputValue(Object value, int maxChars) {
        if (value == null) return "";
        String serialized;
        if (value instanceof CharSequence sequence) serialized = sequence.toString();
        else {
            try {
                serialized = JSON.toJSONString(value);
            } catch (RuntimeException ignored) {
                serialized = String.valueOf(value);
            }
        }
        return OpsMemoryTextUtils.abbreviate(serialized, maxChars);
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null || !StringUtils.hasText(key)) return "";
        return text(node.getConfig().get(key));
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (StringUtils.hasText(normalized)) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    interface ContextPolicy {
        String executionNodeType(OpsWorkflowNode node);

        String incomingRouteKey(OpsAgentDefinition definition, OpsWorkflowNode node);

        String analysisAgentRole(OpsWorkflowNode node);

        boolean edgeActive(OpsGraphEdge edge, OverAllState state);

        Map<String, Object> edgeRuntimeMetadata(OpsAgentDefinition definition,
                                                OverAllState state,
                                                OpsGraphEdge edge);
    }
}
