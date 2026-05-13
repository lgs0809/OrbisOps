package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagQualityCaseSaveCommand;
import cn.lgs.orbisops.application.rag.RagQualityEvalCase;
import cn.lgs.orbisops.application.rag.RagQualityProbeCommand;
import cn.lgs.orbisops.application.rag.RagQualityProbeResult;
import cn.lgs.orbisops.application.rag.RagQualityProbeUseCase;
import cn.lgs.orbisops.application.rag.RagQualityRunCommand;
import cn.lgs.orbisops.application.rag.RagQualityRunResult;
import cn.lgs.orbisops.application.rag.RagQualityRunUseCase;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagQualityEvalService {

    private final RagQualityEvalSettings settings;
    private final RagQualityCaseCatalogUseCase caseCatalogUseCase;
    private final RagQualityProbeUseCase<Map<String, Object>> probeUseCase;
    private final RagQualityRunUseCase<Map<String, Object>> runUseCase;
    private final OpsRagQualityEvalViewMapper viewMapper;

    public RagQualityEvalService(OpsRagQualityEvalManagementAssembly assembly) {
        if (assembly == null) {
            throw new IllegalArgumentException("RAG_QUALITY_EVAL_ASSEMBLY_REQUIRED");
        }
        this.settings = assembly.settings();
        this.caseCatalogUseCase = assembly.caseCatalogUseCase();
        this.probeUseCase = assembly.probeUseCase();
        this.runUseCase = assembly.runUseCase();
        this.viewMapper = assembly.viewMapper();
    }

    @PostConstruct
    public void init() {
        try {
            caseCatalogUseCase.initialize();
        } catch (Exception e) {
            log.warn("RAG 评测表初始化失败，评测能力暂时降级：{}", e.getMessage());
        }
    }

    public Map<String, Object> probe(Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        Integer requestedTopK = toInteger(safeRequest.get("topK"));
        RagQualityProbeCommand command = new RagQualityProbeCommand(
                asString(safeRequest.get("query")),
                asString(safeRequest.get("knowledgeTag")),
                asStringList(safeRequest.get("expectedKeywords")),
                requestedTopK == null ? 8 : requestedTopK,
                asString(safeRequest.get("retrievalMode")),
                safeRequest.containsKey("rerankEnabled")
                        ? Boolean.TRUE.equals(safeRequest.get("rerankEnabled"))
                        : settings.rerankEnabled());
        RagQualityProbeResult<Map<String, Object>> result = probeUseCase.probe(command);
        return viewMapper.probeView(result);
    }

    public List<Map<String, Object>> listCases(Boolean enabled, int limit) {
        return viewMapper.caseViews(caseCatalogUseCase.list(enabled, limit));
    }

    public Map<String, Object> saveCase(Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        Integer requestedTopK = toInteger(safeRequest.get("topK"));
        RagQualityCaseSaveCommand command = new RagQualityCaseSaveCommand(
                toLongObject(safeRequest.get("id")),
                asString(safeRequest.get("caseName")),
                asString(safeRequest.get("query")),
                asString(safeRequest.get("knowledgeTag")),
                asStringList(safeRequest.get("expectedKeywords")),
                requestedTopK == null ? 8 : requestedTopK,
                !Boolean.FALSE.equals(safeRequest.get("enabled")));
        return viewMapper.caseView(caseCatalogUseCase.save(command));
    }

    public boolean deleteCase(Long id) {
        return caseCatalogUseCase.delete(id);
    }

    public Map<String, Object> run(Map<String, Object> request) {
        List<RagQualityEvalCase> cases = evalCasesFrom(request);
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        RagQualityRunCommand command = new RagQualityRunCommand(
                cases,
                asString(safeRequest.get("retrievalMode")),
                safeRequest.containsKey("rerankEnabled")
                        ? Boolean.TRUE.equals(safeRequest.get("rerankEnabled"))
                        : settings.rerankEnabled());
        RagQualityRunResult<Map<String, Object>> result = runUseCase.run(command);
        return viewMapper.runView(result);
    }

    private List<RagQualityEvalCase> evalCasesFrom(Map<String, Object> request) {
        Object requestCases = request == null ? null : request.get("cases");
        if (requestCases instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(value -> evalCase((Map<String, Object>) value))
                    .toList();
        }
        return caseCatalogUseCase.enabledCases(200);
    }

    private RagQualityEvalCase evalCase(Map<String, Object> value) {
        Integer requestedTopK = toInteger(value == null
                ? null
                : value.getOrDefault("topK", value.get("top_k")));
        return new RagQualityEvalCase(
                firstText(value, "caseName", "case_name"),
                firstText(value, "query", "query_text"),
                firstText(value, "knowledgeTag", "knowledge_tag"),
                asStringList(value == null
                        ? null
                        : value.getOrDefault("expectedKeywords", value.get("expected_keywords_json"))),
                requestedTopK == null ? 8 : requestedTopK);
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::asString).filter(StringUtils::hasText).distinct().toList();
        }
        String text = asString(value);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                return JSON.parseArray(text).stream().map(this::asString).filter(StringUtils::hasText).distinct().toList();
            } catch (Exception ignored) {
            }
        }
        return java.util.Arrays.stream(text.split("[\\n,，;；、]+")).map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
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

    private Long toLongObject(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

}
