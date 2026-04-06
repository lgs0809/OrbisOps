package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversation turn extraction boundary.
 *
 * Owns role-line recognition, turn grouping, global role ordering and turn range
 * metadata while leaving the one-turn paragraph fallback to the parser facade.
 */
public final class RagConversationChunkExtractor {

    private static final Pattern CONVERSATION_TURN_PATTERN = Pattern.compile(
            "^(?:\\[?\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[^\\]]*\\]?\\s*)?(用户|客户|客服|运维|开发|系统|告警|机器人|助手|agent|assistant|user|support|operator|bot)\\s*[:：].*",
            Pattern.CASE_INSENSITIVE);

    private final RagChunkMaterializer chunkMaterializer;

    public RagConversationChunkExtractor(RagChunkMaterializer chunkMaterializer) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
    }

    public Extraction extract(String text, Map<String, Object> baseMetadata) {
        List<String> turns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Set<String> roles = new LinkedHashSet<>();
        for (String line : normalize(text).split("\n", -1)) {
            Matcher matcher = CONVERSATION_TURN_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (!current.isEmpty()) {
                    turns.add(current.toString());
                    current.setLength(0);
                }
                roles.add(matcher.group(1));
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            turns.add(current.toString());
        }
        if (turns.size() <= 1) {
            return new Extraction(false, List.of());
        }

        List<RagChunkDraft> drafts = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int turnStart = 1;
        for (int i = 0; i < turns.size(); i++) {
            String turn = turns.get(i);
            if (chunk.length() + turn.length() > chunkMaterializer.maxSegmentChars(baseMetadata)
                    && !chunk.isEmpty()) {
                drafts.add(draft(chunk.toString(), baseMetadata, turnStart, i, roles));
                chunk.setLength(0);
                turnStart = i + 1;
            }
            chunk.append(turn).append('\n');
        }
        if (!chunk.isEmpty()) {
            drafts.add(draft(chunk.toString(), baseMetadata, turnStart, turns.size(), roles));
        }
        return new Extraction(true, drafts);
    }

    public boolean looksLikeConversation(String text) {
        long matches = normalize(text).lines()
                .limit(80)
                .filter(line -> CONVERSATION_TURN_PATTERN.matcher(line).matches())
                .count();
        return matches >= 2;
    }

    private RagChunkDraft draft(String text,
                                Map<String, Object> baseMetadata,
                                int turnStart,
                                int turnEnd,
                                Set<String> roles) {
        return RagChunkDraft.exact(text, chunkMaterializer.mergeMetadata(baseMetadata,
                "chunk_strategy", "conversation-turns",
                "turn_start", turnStart,
                "turn_end", turnEnd,
                "roles", String.join(",", roles)));
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }

    public record Extraction(boolean structured, List<RagChunkDraft> drafts) {
        public Extraction {
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
        }
    }
}
