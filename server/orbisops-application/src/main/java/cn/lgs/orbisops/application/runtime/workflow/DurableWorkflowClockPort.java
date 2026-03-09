package cn.lgs.orbisops.application.runtime.workflow;

import java.time.Instant;

@FunctionalInterface
public interface DurableWorkflowClockPort {
    Instant now();
}
