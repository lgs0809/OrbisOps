package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Typed command for one batch RAG quality evaluation run. */
public record RagQualityRunCommand(
        List<RagQualityEvalCase> cases,
        String retrievalMode,
        boolean rerankEnabled) {

    public RagQualityRunCommand {
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException(
                    "没有可执行的 RAG 评测用例，请先创建启用状态的用例，或在请求体传入 cases。");
        }
        retrievalMode = retrievalMode == null ? "" : retrievalMode.trim();
    }
}
