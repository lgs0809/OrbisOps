package cn.lgs.orbisops.application.schedule;

/** Runtime protocol boundary for preparing and executing a scheduled Agent analysis. */
public interface ScheduledTaskAnalysisPort<Q, R> {

    Q prepare(ScheduledTaskExecutionCommand command, Long executionId);

    String serializeInput(Q request);

    R execute(Q request);

    String renderOutput(R response);

    String runtimeStatus(R response);
}
