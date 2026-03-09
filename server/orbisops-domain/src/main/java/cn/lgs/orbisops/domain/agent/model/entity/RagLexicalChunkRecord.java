package cn.lgs.orbisops.domain.agent.model.entity;

import java.util.Map;

public record RagLexicalChunkRecord(String id, String text, Map<String, Object> metadata) {
}
