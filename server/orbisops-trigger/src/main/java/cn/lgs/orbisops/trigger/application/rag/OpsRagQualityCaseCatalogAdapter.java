package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseCatalogPort;
import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import cn.lgs.orbisops.application.rag.RagQualityCaseSaveCommand;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Trigger adapter that isolates repository maps, snake-case columns and JSON serialization. */
public final class OpsRagQualityCaseCatalogAdapter implements RagQualityCaseCatalogPort {

    private final IRagEvalRepository repository;

    public OpsRagQualityCaseCatalogAdapter(IRagEvalRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("RAG_EVAL_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public void ensureReady() {
        repository.ensureTables();
    }

    @Override
    public List<RagQualityCaseRecord> list(Boolean enabled, int limit) {
        return records(repository.listCases(enabled, limit));
    }

    @Override
    public RagQualityCaseRecord save(RagQualityCaseSaveCommand command) {
        Long id = repository.saveCase(
                command.id(),
                command.caseName(),
                command.query(),
                command.knowledgeTag(),
                JSON.toJSONString(command.expectedKeywords()),
                command.topK(),
                command.enabled());
        return new RagQualityCaseRecord(
                id,
                command.caseName(),
                command.query(),
                command.knowledgeTag(),
                command.expectedKeywords(),
                command.topK(),
                command.enabled());
    }

    @Override
    public boolean delete(Long id) {
        return repository.deleteCase(id);
    }

    @Override
    public List<RagQualityCaseRecord> listEnabled(int limit) {
        return records(repository.listEnabledCases(limit));
    }

    private List<RagQualityCaseRecord> records(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::record).toList();
    }

    private RagQualityCaseRecord record(Map<String, Object> row) {
        Object enabledValue = first(row, "enabled");
        boolean enabled = enabledValue instanceof Boolean value
                ? value
                : toInteger(enabledValue) == null || toInteger(enabledValue) == 1;
        Integer topK = toInteger(first(row, "topK", "top_k"));
        return new RagQualityCaseRecord(
                toLong(first(row, "id")),
                text(first(row, "caseName", "case_name")),
                text(first(row, "query", "query_text")),
                text(first(row, "knowledgeTag", "knowledge_tag")),
                stringList(first(row, "expectedKeywords", "expected_keywords_json")),
                topK == null ? 8 : topK,
                enabled);
    }

    private Object first(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::text).filter(StringUtils::hasText).distinct().toList();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                return JSON.parseArray(text).stream()
                        .map(this::text)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
            } catch (Exception ignored) {
                // Fall through to delimiter parsing for legacy malformed rows.
            }
        }
        return java.util.Arrays.stream(text.split("[\\n,，;；、]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
