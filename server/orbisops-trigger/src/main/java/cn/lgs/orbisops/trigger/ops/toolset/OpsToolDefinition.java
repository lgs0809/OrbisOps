package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolGovernance;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsToolDefinition {

    private String toolName;
    private String displayName;
    private String description;
    private String parametersJson;
    private String outputSchemaJson;
    private String adapterType;
    private String commandTemplate;
    private String mcpServerId;
    private String remoteToolName;
    private String httpConfigJson;
    private String dbConfigJson;
    private boolean readOnly;
    private boolean writesRepairWorkspace;
    private boolean writesTargetResource;
    private boolean requiresChangePackage;
    private boolean requiresApproval;
    private String riskLevel;
    private String outputBudgetJson;
    private boolean enabled;
    private ToolProviderDescriptor providerDescriptor;
    private ToolSemantics semantics;
    private ToolGovernance governance;
}
