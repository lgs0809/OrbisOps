package cn.lgs.orbisops.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾问配置，值对象
 *
 * 2025/6/27 18:42
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    /**
     * 顾问ID
     */
    private String advisorId;

    /**
     * 顾问名称
     */
    private String advisorName;

    /**
     * 顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)
     */
    private String advisorType;

    /**
     * 顺序号
     */
    private Integer orderNum;

    /**
     * 扩展；记忆
     */
    private ChatMemory chatMemory;

    /**
     * 扩展；rag 问答
     */
    private RagAnswer ragAnswer;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMemory {
        private int maxMessages;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagAnswer {
        private int topK = 4;
        private String filterExpression;
        /**
         * vector | bm25 | hybrid | auto
         */
        private String retrievalMode = "vector";
        private int vectorTopK = 4;
        private int bm25TopK = 6;
        private int finalTopK = 6;
        private int maxContextChars = 12000;
        private boolean dynamicSearch = true;
        /**
         * rule | llm | hybrid。rule 保持低延迟规则扩展；hybrid 在复杂问题或低召回时追加 LLM rewrite。
         */
        private String queryRewriteMode = "rule";
        private Boolean llmQueryRewriteEnabled = false;
        private String llmQueryRewriteBaseUrl;
        private String llmQueryRewriteApiKey;
        private String llmQueryRewritePath = "v1/chat/completions";
        private String llmQueryRewriteModel = "gpt-5.4-mini";
        private int llmQueryRewriteMaxQueries = 4;
        private int llmQueryRewriteTimeoutSeconds = 2;
        private int llmQueryRewriteMinChars = 18;
        private Boolean llmQueryRewriteOnLowRecall = true;
        private int llmQueryRewriteLowRecallMinCandidates = 2;
        /**
         * none | voyage | cohere
         */
        private Boolean rerankEnabled = true;
        private String rerankProvider = "cohere";
        private String rerankBaseUrl = "http://127.0.0.1:8110";
        private String rerankApiKey = "local-rag-models";
        private String rerankPath = "v1/rerank";
        private String rerankModel = "Qwen/Qwen3-VL-Reranker-2B";
        private int rerankCandidateTopK = 20;
        private int rerankTopN = 6;
        private int rerankMaxDocChars = 1200;
    }

}
