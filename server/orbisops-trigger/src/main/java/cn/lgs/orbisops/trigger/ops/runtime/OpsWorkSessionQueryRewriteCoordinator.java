package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.trigger.ops.OpsLlmDegradationException;
import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionQueryRewriteTrace.*;

/** Owns query-rewrite gating, traced execution, degradation and request projection. */
final class OpsWorkSessionQueryRewriteCoordinator {

    private final OpsMainQuestionRewriteService questionRewriteService;
    private final OpsQueryRewriteSafetyIntentPolicy safetyIntentPolicy = new OpsQueryRewriteSafetyIntentPolicy();
    private final OpsQueryRewriteResourceIntegrityPolicy resourceIntegrityPolicy = new OpsQueryRewriteResourceIntegrityPolicy();

    OpsWorkSessionQueryRewriteCoordinator(
            OpsMainQuestionRewriteService questionRewriteService) {
        this.questionRewriteService = questionRewriteService;
    }

    void rewrite(OpsAgentDefinition definition,
                 OpsAgentChatRequest request,
                 OpsRuntimeExecutionPlan plan,
                 String memoryContext,
                 String originalQuery,
                 List<OpsRuntimeEvent> events,
                 Consumer<OpsRuntimeEvent> eventSink,
                 long requestStartedNanos) {
        if (shouldSkip(
                definition,
                request,
                plan,
                memoryContext,
                originalQuery,
                events,
                eventSink,
                requestStartedNanos)) {
            return;
        }

        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("QUERY_REWRITE_STARTED")
                .nodeId("main-query-rewrite")
                .nodeType("QUERY_REWRITE")
                .agent("main-agent")
                .status("RUNNING")
                .summary("主 Agent 开始结合会话记忆重写当前问题。")
                .payload(payloadWithElapsed(questionTrace(
                        "originalQuestion",
                        originalQuery,
                        Map.of("memoryContextChars", memoryContext.length())),
                        requestStartedNanos))
                .build());
        OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                events,
                eventSink,
                "MAIN_QUERY_REWRITE",
                "main-query-rewrite",
                "QUERY_REWRITE",
                "main-agent",
                "memory", request.getTrustedSkillFrame());
        OpsMainQuestionRewriteService.RewriteResult result;
        try {
            result = OpsLlmTraceContext.withTrace(
                    trace,
                    () -> questionRewriteService.rewrite(
                            originalQuery,
                            memoryContext));
        } catch (OpsLlmDegradationException error) {
            request.getMetadata().put(
                    OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY,
                    value(originalQuery));
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("QUERY_REWRITE_DEGRADED")
                    .nodeId("main-query-rewrite")
                    .nodeType("QUERY_REWRITE")
                    .agent("main-agent")
                    .status("DEGRADED")
                    .summary("主 Agent 问题重写 LLM 不可用，保持原问题继续受控 Work Session："
                            + value(error.getMessage()))
                    .payload(payloadWithElapsed(questionTrace(
                            "originalQuestion",
                            originalQuery,
                            Map.of(
                                    "reason", value(error.getMessage()),
                                    "memoryContextChars", memoryContext.length())),
                            requestStartedNanos))
                    .build());
            return;
        }

        String candidate = StringUtils.hasText(result.rewrittenQuestion())
                ? result.rewrittenQuestion()
                : originalQuery;
        boolean resourceDrift = resourceIntegrityPolicy.introducesUnknownApiPath(
                originalQuery,
                memoryContext,
                candidate);
        boolean premiseDrift = resourceIntegrityPolicy.introducesUnsupportedOperationalPremise(
                originalQuery,
                candidate);
        boolean rewriteRejected = resourceDrift || premiseDrift;
        String rewritten = rewriteRejected ? originalQuery : candidate;
        boolean changed = !rewriteRejected
                && result.changed()
                && StringUtils.hasText(rewritten)
                && !rewritten.equals(originalQuery);
        if (changed) {
            request.setQuery(rewritten);
            request.getMetadata().put(
                    OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY,
                    rewritten);
            refreshAnalysisQuestionContext(request, rewritten);
        } else {
            request.getMetadata().put(
                    OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY,
                    value(originalQuery));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(questionTrace("originalQuestion", originalQuery, Map.of()));
        payload.putAll(questionTrace("rewrittenQuestion", rewritten, Map.of()));
        payload.put("changed", changed);
        payload.put("rewriteRejected", rewriteRejected);
        payload.put("reason", resourceDrift
                ? "QUERY_REWRITE_UNKNOWN_API_PATH_REJECTED"
                : premiseDrift
                        ? "QUERY_REWRITE_UNSUPPORTED_OPERATIONAL_PREMISE_REJECTED"
                        : value(result.reason()));
        payload.put("resolvedReferences", result.resolvedReferences());
        payload.put("memoryContextChars", memoryContext.length());
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("QUERY_REWRITE_FINISHED")
                .nodeId("main-query-rewrite")
                .nodeType("QUERY_REWRITE")
                .agent("main-agent")
                .status("SUCCEEDED")
                .summary(resourceDrift
                        ? "主 Agent 问题重写尝试引入用户本轮未明确提供的 API 路径，已保留用户原问题。"
                        : premiseDrift
                                ? "主 Agent 问题重写尝试强化用户未确认的运维事实前提，已保留用户原问题。"
                                : changed
                                        ? "主 Agent 已生成独立问题：" + preview(rewritten)
                                        : "主 Agent 判断问题已足够独立。")
                .payload(payloadWithElapsed(payload, requestStartedNanos))
                .build());
    }

    private boolean shouldSkip(OpsAgentDefinition definition,
                               OpsAgentChatRequest request,
                               OpsRuntimeExecutionPlan plan,
                               String memoryContext,
                               String originalQuery,
                               List<OpsRuntimeEvent> events,
                               Consumer<OpsRuntimeEvent> eventSink,
                               long requestStartedNanos) {
        if (trustedLanding(request)) {
            skip(request, originalQuery, events, eventSink, requestStartedNanos,
                    "已审批 LANDING 使用服务端冻结的 ChangePackage task book，跳过面向用户会话的 Query Rewrite。",
                    Map.of("reason", "LANDING_TRUSTED_TASK_BOOK"));
            return true;
        }
        if (safetyIntentPolicy.mustPreserveOriginal(originalQuery)) {
            skip(request, originalQuery, events, eventSink, requestStartedNanos,
                    "检测到安全关键运维意图，禁止 Query Rewrite 改写权限、审批、验证或生产副作用语义。",
                    Map.of("reason", "SAFETY_CRITICAL_INTENT_PRESERVED"));
            return true;
        }
        if (definition != null
                && Boolean.FALSE.equals(definition.getQueryRewriteEnabled())) {
            skip(request, originalQuery, events, eventSink, requestStartedNanos,
                    "当前 Agent 编排已关闭主 Agent 问题重写。",
                    Map.of("queryRewriteEnabled", false));
            return true;
        }
        if (!StringUtils.hasText(memoryContext)) {
            skip(request, originalQuery, events, eventSink, requestStartedNanos,
                    "没有需要消解的会话上下文，保留用户原问题直接执行。",
                    Map.of("reason", "NO_CONVERSATION_CONTEXT"));
            return true;
        }
        return false;
    }

    private boolean trustedLanding(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return false;
        Object raw = request.getMetadata().get(OpsAgentRunExecutionContextFactory.AUTHORITATIVE_KEY);
        return raw instanceof AgentRunExecutionContext context
                && context.stage() == AgentExecutionStage.LANDING
                && context.approvedPackage().isPresent();
    }

    private void skip(OpsAgentChatRequest request,
                      String originalQuery,
                      List<OpsRuntimeEvent> events,
                      Consumer<OpsRuntimeEvent> eventSink,
                      long startedNanos,
                      String summary,
                      Map<String, Object> extra) {
        request.getMetadata().put(
                OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY,
                value(originalQuery));
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.putAll(questionTrace("originalQuestion", originalQuery, Map.of()));
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("QUERY_REWRITE_SKIPPED")
                .nodeId("main-query-rewrite")
                .nodeType("QUERY_REWRITE")
                .agent("main-agent")
                .status("SKIPPED")
                .summary(summary)
                .payload(payloadWithElapsed(payload, startedNanos))
                .build());
    }

    private void refreshAnalysisQuestionContext(
            OpsAgentChatRequest request,
            String rewritten) {
        request.getMetadata().put(
                WorkSessionMetadataKeys.OPS_ANALYSIS_QUESTION_CONTEXT,
                OpsQuestionContext.from(rewritten));
        Object analysisRequest = request.getMetadata().get(
                WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST);
        if (analysisRequest instanceof OpsAgentRunRequestDTO dto) {
            dto.setQuery(rewritten);
            dto.setQuestion(rewritten);
        }
    }
}
