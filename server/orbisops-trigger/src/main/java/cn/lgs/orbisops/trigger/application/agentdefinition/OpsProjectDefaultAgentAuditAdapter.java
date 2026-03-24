package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Trigger audit adapter for the project default Agent bootstrap process. */
public final class OpsProjectDefaultAgentAuditAdapter
        implements ProjectDefaultAgentAuditPort<OpsAgentDefinition> {

    private final OpsConfigAuditService auditService;
    private final OpsAgentDefinitionViewMapper viewMapper;

    public OpsProjectDefaultAgentAuditAdapter(
            OpsConfigAuditService auditService,
            OpsAgentDefinitionViewMapper viewMapper) {
        if (auditService == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
        this.viewMapper = viewMapper == null
                ? new OpsAgentDefinitionViewMapper()
                : viewMapper;
    }

    @Override
    public void record(
            String action,
            String agentId,
            OpsAgentDefinition previousDefinition,
            OpsAgentDefinition publishedDefinition,
            String suiteId,
            String evalRunId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("agent", viewMapper.view(publishedDefinition));
        after.put("evalRunId", text(evalRunId));
        after.put("suiteId", text(suiteId));
        auditService.record(
                "agent-definition",
                action,
                agentId,
                previousDefinition == null ? null : viewMapper.view(previousDefinition),
                after);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
