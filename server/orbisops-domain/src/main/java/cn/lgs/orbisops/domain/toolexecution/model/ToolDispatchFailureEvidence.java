package cn.lgs.orbisops.domain.toolexecution.model;

/** Trusted provider-boundary evidence, never inferred from exception text or model output. */
public interface ToolDispatchFailureEvidence {
    boolean dispatched();
}
