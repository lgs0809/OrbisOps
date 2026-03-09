package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextToolsetPort;
import cn.lgs.orbisops.application.toolset.ToolsetApplicationService;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolPolicySnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolRisk;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolsetSnapshot;
import cn.lgs.orbisops.domain.toolset.model.ToolDefinition;
import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Cross-context ACL from Toolset aggregates to Runtime Context published snapshots. */
@Component
public class OpsRuntimeContextToolsetAdapter implements RuntimeContextToolsetPort {

    private final ObjectProvider<ToolsetApplicationService> toolsetProvider;

    public OpsRuntimeContextToolsetAdapter(
            ObjectProvider<ToolsetApplicationService> toolsetProvider) {
        if (toolsetProvider == null) {
            throw new IllegalArgumentException("TOOLSET_APPLICATION_PROVIDER_REQUIRED");
        }
        this.toolsetProvider = toolsetProvider;
    }

    @Override
    public List<RuntimeContextToolsetSnapshot> listEffective(
            String projectId,
            String actor) {
        ToolsetApplicationService toolsets = toolsetProvider.getIfAvailable();
        if (toolsets == null) {
            throw new IllegalStateException(
                    "ToolsetApplicationService 未初始化，Runtime Context Bundle 不能采信 request toolset refs");
        }
        return toolsets.effective(projectId, actor).stream()
                .filter(ToolsetDefinition::enabled)
                .map(this::snapshot)
                .toList();
    }

    private RuntimeContextToolsetSnapshot snapshot(ToolsetDefinition toolset) {
        return new RuntimeContextToolsetSnapshot(
                toolset.toolsetId(),
                toolset.adapterType(),
                toolset.readOnlyDefault(),
                toolset.tools().stream()
                        .filter(ToolDefinition::enabled)
                        .map(this::policy)
                        .toList());
    }

    private RuntimeContextToolPolicySnapshot policy(ToolDefinition tool) {
        return new RuntimeContextToolPolicySnapshot(
                tool.toolName(),
                tool.adapterType(),
                tool.readOnly(),
                tool.writesRepairWorkspace(),
                tool.writesTargetResource(),
                tool.requiresChangePackage(),
                tool.requiresApproval(),
                RuntimeContextToolRisk.from(tool.riskLevel().name()),
                tool.parametersJson());
    }
}
