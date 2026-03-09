package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleAgentSnapshot;
import cn.lgs.orbisops.application.schedule.TaskScheduleAgentSnapshotPort;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

/** Agent Definition query adapter for schedule binding snapshots. */
public final class OpsTaskScheduleAgentSnapshotAdapter implements TaskScheduleAgentSnapshotPort {

    private final OpsAgentDefinitionQueryGateway queryGateway;

    public OpsTaskScheduleAgentSnapshotAdapter(OpsAgentDefinitionQueryGateway queryGateway) {
        if (queryGateway == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_AGENT_QUERY_GATEWAY_REQUIRED");
        }
        this.queryGateway = queryGateway;
    }

    @Override
    public TaskScheduleAgentSnapshot resolve(String agentId, Integer version, String projectId) {
        OpsAgentDefinition definition = queryGateway.resolveForProject(agentId, version, false, projectId);
        return definition == null
                ? null
                : new TaskScheduleAgentSnapshot(definition.getVersion(), definition.getDefinitionHash());
    }
}
