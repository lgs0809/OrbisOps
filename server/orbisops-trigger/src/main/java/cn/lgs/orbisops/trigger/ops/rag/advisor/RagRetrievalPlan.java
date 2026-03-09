package cn.lgs.orbisops.trigger.ops.rag.advisor;

/**
 * Framework-neutral retrieval execution plan resolved before any recall adapter is invoked.
 */
public record RagRetrievalPlan(String mode,
                               int vectorTopK,
                               int bm25TopK,
                               int finalTopK,
                               int maxContextChars,
                               boolean rerankEnabled,
                               String rerankProvider,
                               String rerankBaseUrl,
                               String rerankPath,
                               String rerankModel,
                               int rerankCandidateTopK,
                               int rerankTopN,
                               int rerankMaxDocChars) {
}
