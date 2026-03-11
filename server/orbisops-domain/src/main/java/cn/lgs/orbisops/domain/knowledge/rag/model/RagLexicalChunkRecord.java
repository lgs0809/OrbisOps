package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.util.Map;

public record RagLexicalChunkRecord(String id, String text, Map<String, Object> metadata) {
}
