package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Thin node-level RAG orchestration facade. */
@Slf4j
@Service
public final class OpsNodeRagService {

    private final OpsNodeRagAdvisorFactory advisorFactory;
    private final OpsNodeRagRetrievalPlanner retrievalPlanner;
    private final OpsNodeRagAuditRenderer auditRenderer;
    private final OpsNodeRagSettings settings;

    public OpsNodeRagService(
            OpsNodeRagAdvisorFactory advisorFactory,
            OpsNodeRagRetrievalPlanner retrievalPlanner,
            OpsNodeRagAuditRenderer auditRenderer,
            OpsNodeRagSettings settings) {
        if (advisorFactory == null
                || retrievalPlanner == null
                || auditRenderer == null
                || settings == null) {
            throw new IllegalArgumentException("NODE_RAG_DEPENDENCIES_REQUIRED");
        }
        this.advisorFactory = advisorFactory;
        this.retrievalPlanner = retrievalPlanner;
        this.auditRenderer = auditRenderer;
        this.settings = settings;
    }

    public String enhancePrompt(
            String query,
            Boolean ragEnabled,
            String knowledgeBaseId,
            List<OpsRuntimeEvent> events) {
        return enhancePrompt(
                query, query, ragEnabled, knowledgeBaseId,
                events, null, false);
    }

    public String enhancePrompt(
            String query,
            Boolean ragEnabled,
            String knowledgeBaseId,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return enhancePrompt(
                query, query, ragEnabled, knowledgeBaseId,
                events, eventSink, false);
    }

    public String enhancePrompt(
            String prompt,
            String retrievalQuery,
            Boolean ragEnabled,
            String knowledgeBaseId,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return enhancePrompt(
                prompt, retrievalQuery, ragEnabled, knowledgeBaseId,
                events, eventSink, false);
    }

    public String enhancePrompt(
            String prompt,
            String retrievalQuery,
            Boolean ragEnabled,
            String knowledgeBaseId,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            boolean streamingResponse) {
        return enhancePrompt(
                prompt, retrievalQuery, ragEnabled, knowledgeBaseId,
                events, eventSink, streamingResponse, "", "");
    }

    public String enhancePrompt(
            String prompt,
            String retrievalQuery,
            Boolean ragEnabled,
            String knowledgeBaseId,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            boolean streamingResponse,
            String projectId,
            String knowledgeBaseScope) {
        if (!Boolean.TRUE.equals(ragEnabled)) {
            return prompt;
        }
        OpsNodeRagAdvisorFactory.Resources resources =
                advisorFactory.resolve(settings);
        OpsNodeRagRetrievalPlanner.Plan plan = retrievalPlanner.plan(
                prompt,
                retrievalQuery,
                streamingResponse,
                projectId,
                knowledgeBaseId,
                knowledgeBaseScope,
                resources,
                settings);
        if (resources.vectorStore() == null && !resources.bm25Available()) {
            auditRenderer.blocked(
                    events,
                    eventSink,
                    "RAG 未配置 VectorStore/PgVector，跳过节点级检索。");
            return plan.prompt();
        }
        if (!resources.embeddingAvailable() && !resources.bm25Available()) {
            auditRenderer.blocked(
                    events,
                    eventSink,
                    advisorFactory.embeddingUnavailableMessage());
            return plan.prompt();
        }
        long startedNanos = System.nanoTime();
        auditRenderer.started(events, eventSink, plan, settings);
        try {
            Map<String, Object> context =
                    retrievalPlanner.advisorContext(plan, settings);
            List<Document> documents = advisorFactory.retrieve(
                    resources,
                    plan.finalTopK(),
                    settings,
                    plan.retrievalQuery(),
                    context);
            auditRenderer.completed(
                    events, eventSink, plan, settings, documents, startedNanos);
            auditRenderer.degraded(events, eventSink, context);
            return auditRenderer.renderPrompt(plan, documents);
        } catch (Exception error) {
            if (settings.failOnLlmDegradation()) {
                throw error instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException(
                                "节点级 RAG 检索失败：" + error.getMessage(), error);
            }
            log.warn("节点级 RAG 检索失败：{}", error.getMessage());
            auditRenderer.failed(
                    events, eventSink, plan, error, startedNanos);
            return plan.prompt();
        }
    }
}
