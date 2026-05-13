package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionPlan;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionProjection;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic policy for context compression partitioning, fallback summary and durable projection. */
public class MemoryCompressionPolicy {

    private final MemoryContentHashPolicy hashPolicy;

    public MemoryCompressionPolicy(MemoryContentHashPolicy hashPolicy) {
        this.hashPolicy = hashPolicy == null ? new MemoryContentHashPolicy() : hashPolicy;
    }

    public MemoryCompressionPlan plan(List<ColdMemoryMessageSnapshot> messages,
                                      int thresholdMessages,
                                      int keepRecent) {
        if (messages == null || messages.size() < Math.max(4, thresholdMessages)) {
            return MemoryCompressionPlan.none();
        }
        int keep = Math.max(2, Math.min(keepRecent, messages.size() - 1));
        int split = messages.size() - keep;
        return new MemoryCompressionPlan(
                messages.subList(0, split),
                messages.subList(split, messages.size()));
    }

    public MemoryCompressionSummary ruleSummary(List<ColdMemoryMessageSnapshot> messages) {
        List<ColdMemoryMessageSnapshot> safe = messages == null ? List.of() : messages;
        List<String> userSignals = new ArrayList<>();
        List<String> assistantSignals = new ArrayList<>();
        List<String> inheritedSummaries = new ArrayList<>();
        String startedAt = "";
        String endedAt = "";
        for (ColdMemoryMessageSnapshot message : safe) {
            if (message == null) {
                continue;
            }
            if (!hasText(startedAt)) {
                startedAt = value(message.createdAt());
            }
            endedAt = value(message.createdAt());
            if ("summary".equals(message.metadata().get("memory_type"))) {
                inheritedSummaries.add(message.content());
            } else if ("user".equalsIgnoreCase(message.role())) {
                userSignals.add(abbreviate(message.content(), 120));
            } else if ("assistant".equalsIgnoreCase(message.role())) {
                assistantSignals.add(abbreviate(message.content(), 160));
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("会话压缩摘要：共压缩 ").append(safe.size()).append(" 条历史消息");
        if (hasText(startedAt) || hasText(endedAt)) {
            builder.append("，时间范围 ").append(startedAt).append(" 至 ").append(endedAt);
        }
        builder.append("。\n");
        appendSection(builder, "继承的历史摘要（历史数据）", inheritedSummaries, Integer.MAX_VALUE);
        appendSection(builder, "用户关注", userSignals, 4);
        appendSection(builder, "已有结论", assistantSignals, 4);
        return new MemoryCompressionSummary(builder.toString().trim(), "context_compressor");
    }

    /** Retain complete source references for explicit constraints and unresolved work outside free-form summarization. */
    public List<ColdMemoryMessageSnapshot> protectedMessages(List<ColdMemoryMessageSnapshot> previous,
                                                            List<ColdMemoryMessageSnapshot> prefix) {
        Map<String, ColdMemoryMessageSnapshot> result = new LinkedHashMap<>();
        if (previous != null) previous.forEach(message -> result.put(sourceKey(message), message));
        if (prefix != null) {
            for (ColdMemoryMessageSnapshot message : prefix) {
                if (message == null || "summary".equals(message.metadata().get("memory_type"))) continue;
                String content = value(message.content()).toLowerCase(java.util.Locale.ROOT);
                if (content.matches("(?s).*(禁止|不得|不要|必须|只读|约束|待确认|待处理|未完成|待审批|不能|尚未|must|never|do not|read.only|pending|unresolved|todo).*")) {
                    result.put(sourceKey(message), message);
                }
            }
        }
        return List.copyOf(result.values());
    }

    private String sourceKey(ColdMemoryMessageSnapshot message) {
        return message.sessionId() + ":" + message.metadata().getOrDefault("messageSeq", hashPolicy.stableHash(message.content()));
    }

    public MemoryCompressionProjection project(String sessionId,
                                               String userId,
                                               MemoryCompressionPlan plan,
                                               MemoryCompressionSummary summary,
                                               String createdAt) {
        if (plan == null || !plan.required() || summary == null || !hasText(summary.content())) {
            return new MemoryCompressionProjection(List.of(), null);
        }
        String normalizedCreatedAt = value(createdAt);
        Map<String, Object> summaryMetadata = new LinkedHashMap<>();
        summaryMetadata.put("memory_type", "summary");
        summaryMetadata.put("source", "ops_context_compressor");
        ColdMemoryMessageSnapshot summaryMessage = new ColdMemoryMessageSnapshot(
                value(sessionId),
                value(userId),
                "system",
                summary.content(),
                normalizedCreatedAt,
                summaryMetadata);
        List<ColdMemoryMessageSnapshot> replacement = new ArrayList<>();
        replacement.add(summaryMessage);
        replacement.addAll(plan.recentMessages());
        ColdMemoryItemSnapshot summaryItem = new ColdMemoryItemSnapshot(
                value(sessionId),
                value(userId),
                "summary",
                summary.content(),
                BigDecimal.valueOf(0.86D),
                "[\"summary\",\"conversation\"]",
                "system",
                hashPolicy.stableHash(summary.content()),
                Map.of(
                        "source", hasText(summary.source()) ? summary.source() : "context_compressor",
                        "compressedMessages", plan.olderMessages().size()),
                normalizedCreatedAt);
        return new MemoryCompressionProjection(replacement, summaryItem);
    }

    public String trimContext(String context, int maxChars) {
        if (!hasText(context) || context.length() <= maxChars) {
            return context;
        }
        int keepHead = Math.max(500, maxChars / 3);
        int keepTail = Math.max(500, maxChars - keepHead - 80);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalChars", context.length());
        return context.substring(0, Math.min(keepHead, context.length()))
                + "\n... memory context compressed, metadata=" + metadata + " ...\n"
                + context.substring(Math.max(0, context.length() - keepTail));
    }

    private void appendSection(StringBuilder builder, String title, List<String> lines, int limit) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        builder.append(title).append("：");
        lines.stream()
                .filter(this::hasText)
                .limit(Math.max(1, limit))
                .forEach(line -> builder.append("\n- ").append(line));
        builder.append("\n");
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
