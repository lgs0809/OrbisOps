package cn.lgs.orbisops.domain.runtime.taskcontext.exception;

public class TaskContextVersionConflictException extends RuntimeException {

    public TaskContextVersionConflictException(String runId, int expectedVersion) {
        super("TASK_CONTEXT_VERSION_CONFLICT:" + runId + ":" + expectedVersion);
    }
}
