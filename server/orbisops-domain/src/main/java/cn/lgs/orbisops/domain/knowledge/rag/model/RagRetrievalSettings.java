package cn.lgs.orbisops.domain.knowledge.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Runtime retrieval policy for one RAG query. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagRetrievalSettings {

    @Builder.Default
    private int topK = 4;
    private String filterExpression;

    /** vector | bm25 | hybrid | auto. */
    @Builder.Default
    private String retrievalMode = "vector";
    @Builder.Default
    private int vectorTopK = 4;
    @Builder.Default
    private int bm25TopK = 6;
    @Builder.Default
    private int finalTopK = 6;
    @Builder.Default
    private int maxContextChars = 12000;
    @Builder.Default
    private boolean dynamicSearch = true;

    /** rule | llm | hybrid. */
    @Builder.Default
    private String queryRewriteMode = "rule";
    @Builder.Default
    private Boolean llmQueryRewriteEnabled = false;
    private String llmQueryRewriteBaseUrl;
    private String llmQueryRewriteApiKey;
    @Builder.Default
    private String llmQueryRewritePath = "v1/chat/completions";
    @Builder.Default
    private String llmQueryRewriteModel = "";
    @Builder.Default
    private int llmQueryRewriteMaxQueries = 4;
    @Builder.Default
    private int llmQueryRewriteTimeoutSeconds = 2;
    @Builder.Default
    private int llmQueryRewriteMinChars = 18;
    @Builder.Default
    private Boolean llmQueryRewriteOnLowRecall = true;
    @Builder.Default
    private int llmQueryRewriteLowRecallMinCandidates = 2;

    @Builder.Default
    private Boolean rerankEnabled = false;
    @Builder.Default
    private String rerankProvider = "";
    @Builder.Default
    private String rerankBaseUrl = "";
    private String rerankApiKey;
    @Builder.Default
    private String rerankPath = "v1/rerank";
    @Builder.Default
    private String rerankModel = "";
    @Builder.Default
    private int rerankCandidateTopK = 20;
    @Builder.Default
    private int rerankTopN = 6;
    @Builder.Default
    private int rerankMaxDocChars = 1200;
}
