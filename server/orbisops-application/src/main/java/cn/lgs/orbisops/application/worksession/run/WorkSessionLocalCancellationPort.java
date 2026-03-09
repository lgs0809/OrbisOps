package cn.lgs.orbisops.application.worksession.run;

/** Local in-process cancellation boundary paired with durable cancellation state. */
public interface WorkSessionLocalCancellationPort {

    void markCanceled(String runId);
}
