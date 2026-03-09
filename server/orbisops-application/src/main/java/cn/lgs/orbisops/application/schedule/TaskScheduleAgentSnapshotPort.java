package cn.lgs.orbisops.application.schedule;

/** Resolves the project-scoped published Agent Definition snapshot for a schedule. */
public interface TaskScheduleAgentSnapshotPort {

    TaskScheduleAgentSnapshot resolve(String agentId, Integer version, String projectId);
}
