package cn.lgs.orbisops.domain.worksession.runtime.model;

/** How a run decides its next step. Runtime authority is modeled separately. */
public enum AgentExecutionStyle {
    /** LLM observes and dynamically chooses the next capability. */
    REACT,
    /** User-authored drag/drop graph follows its fixed topology. */
    WORKFLOW
}
