package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Records node RAG lifecycle events and renders model-visible retrieval context. */
@Component
public final class OpsNodeRagAuditRenderer {

    public void blocked(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String summary) {
        addEvent(events, eventSink,
                "RAG_RETRIEVE", "BLOCKED", summary, Map.of());
    }

    public void started(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            OpsNodeRagRetrievalPlanner.Plan plan,
            OpsNodeRagSettings settings) {
        Map<String, Object> payload = identityPayload(plan);
        payload.put("retrievalMode", plan.retrievalMode());
        payload.put("streamingOptimized", plan.streamingOptimized());
        payload.put("rerankEnabled", plan.rerankEnabled());
        payload.put("rerankProvider", plan.rerankEnabled()
                ? value(settings.rerank().provider()) : "");
        payload.put("rerankModel", plan.rerankEnabled()
                ? value(settings.rerank().model()) : "");
        payload.put("vectorTopK", plan.vectorTopK());
        payload.put("bm25TopK", plan.bm25TopK());
        payload.put("finalTopK", plan.finalTopK());
        payload.put("retrievalQueryChars", plan.retrievalQuery().length());
        payload.put("promptChars", plan.prompt().length());
        addEvent(events, eventSink,
                "RAG_RETRIEVE_STARTED", "RUNNING",
                "开始节点级 RAG 检索。", payload);
    }

    public void completed(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            OpsNodeRagRetrievalPlanner.Plan plan,
            OpsNodeRagSettings settings,
            List<Document> documents,
            long startedNanos) {
        List<Document> safeDocuments = documents == null ? List.of() : documents;
        Map<String, Object> payload = identityPayload(plan);
        payload.put("documentCount", safeDocuments.size());
        payload.put("retrievalMode", plan.retrievalMode());
        payload.put("streamingOptimized", plan.streamingOptimized());
        payload.put("rerankEnabled", plan.rerankEnabled());
        payload.put("rerankProvider", plan.rerankEnabled()
                ? value(settings.rerank().provider()) : "");
        payload.put("rerankModel", plan.rerankEnabled()
                ? value(settings.rerank().model()) : "");
        payload.put("durationMs", elapsedMs(startedNanos));
        List<Map<String, Object>> sources = renderSources(safeDocuments);
        payload.put("sources", sources);
        String outputHash = sha256(plan.retrievalQuery() + "|" + safeDocuments.size() + "|" + sources);
        payload.put("sourceType", "RAG");
        payload.put("resultId", "rag-result-" + outputHash.substring(0, 16));
        payload.put("evidenceId", "rag-evidence-" + outputHash.substring(0, 16));
        payload.put("outputHash", outputHash);
        payload.put("verified", true);
        addEvent(events, eventSink,
                "RAG_RETRIEVE",
                safeDocuments.isEmpty() ? "NOT_FOUND" : "SUCCEEDED",
                "节点级 RAG 检索完成，命中文档数：" + safeDocuments.size(),
                payload);
    }

    public void degraded(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            Map<String, Object> context) {
        String rewriteError = text(context, "qa_llm_query_rewrite_error");
        if (StringUtils.hasText(rewriteError)) {
            addEvent(events, eventSink,
                    "RAG_QUERY_REWRITE", "DEGRADED",
                    "RAG LLM query rewrite 不可用，已使用规则 rewrite 继续检索。",
                    Map.of("error", rewriteError));
        }
        String rerankError = text(context, "qa_rerank_error");
        if (StringUtils.hasText(rerankError)) {
            addEvent(events, eventSink,
                    "RAG_RERANK", "DEGRADED",
                    "RAG rerank 不可用，已使用融合召回排序继续检索。",
                    Map.of("error", rerankError));
        }
    }

    public void failed(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            OpsNodeRagRetrievalPlanner.Plan plan,
            Exception error,
            long startedNanos) {
        Map<String, Object> payload = identityPayload(plan);
        payload.put("durationMs", elapsedMs(startedNanos));
        payload.put("streamingOptimized", plan.streamingOptimized());
        addEvent(events, eventSink,
                "RAG_RETRIEVE", "ERROR",
                "节点级 RAG 检索失败：" + error.getMessage(), payload);
    }

    public String renderPrompt(
            OpsNodeRagRetrievalPlanner.Plan plan,
            List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return plan.prompt()
                    + "\n\n### RAG 检索结果\n未从知识库检索到可用上下文。";
        }
        return plan.prompt()
                + "\n\n### RAG 检索上下文\n"
                + renderDocuments(documents);
    }

    private String renderDocuments(List<Document> documents) {
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            Map<String, Object> metadata = document.getMetadata() == null
                    ? Map.of()
                    : document.getMetadata();
            String source = String.valueOf(metadata.getOrDefault(
                    "source", metadata.getOrDefault("file_name", document.getId())));
            blocks.add("[" + (i + 1) + "] source=" + source
                    + ", metadata=" + abbreviate(metadata.toString(), 360)
                    + "\n" + abbreviate(document.getText(), 1400));
        }
        return blocks.stream().collect(Collectors.joining("\n\n"));
    }

    private List<Map<String, Object>> renderSources(List<Document> documents) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            Map<String, Object> metadata = document.getMetadata() == null
                    ? Map.of()
                    : document.getMetadata();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", i + 1);
            source.put("docName", String.valueOf(metadata.getOrDefault(
                    "file_name", metadata.getOrDefault("source", document.getId()))));
            source.put("chunkId", String.valueOf(metadata.getOrDefault(
                    "chunk_id", metadata.getOrDefault("id", document.getId()))));
            source.put("knowledgeBaseId",
                    String.valueOf(metadata.getOrDefault("knowledge", "")));
            source.put("score", String.valueOf(metadata.getOrDefault(
                    "score", metadata.getOrDefault("rerank_score", ""))));
            source.put("chunkContent", abbreviate(document.getText(), 480));
            source.put("metadata", metadata);
            sources.add(source);
        }
        return sources;
    }

    private Map<String, Object> identityPayload(
            OpsNodeRagRetrievalPlanner.Plan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", plan.projectId());
        payload.put("knowledgeBaseId", plan.knowledgeBaseId());
        payload.put("knowledgeBaseScope", plan.knowledgeBaseScope());
        return payload;
    }

    private void addEvent(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String type,
            String status,
            String summary,
            Map<String, Object> payload) {
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType(type)
                .status(status)
                .summary(summary)
                .payload(payload == null ? Map.of() : payload)
                .build();
        if (events != null) {
            events.add(event);
        }
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    private String text(Map<String, Object> context, String key) {
        Object value = context == null ? null : context.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
