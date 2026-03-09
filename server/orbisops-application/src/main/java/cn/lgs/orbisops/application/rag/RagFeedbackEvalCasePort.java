package cn.lgs.orbisops.application.rag;

/** Port used by feedback triage to create one quality evaluation case. */
public interface RagFeedbackEvalCasePort {

    RagQualityCaseRecord save(RagQualityCaseSaveCommand command);
}
