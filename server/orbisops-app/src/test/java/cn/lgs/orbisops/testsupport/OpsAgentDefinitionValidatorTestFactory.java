package cn.lgs.orbisops.testsupport;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionResourceValidator;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionValidator;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentGraphDefinitionPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentGraphTopologyPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentMcpServerDefinitionPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentModelKnowledgeReferenceValidator;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentNodeDefinitionPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentProjectCapabilityReferenceValidator;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeDefinitionPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentToolNamePolicy;

/** Creates the production-shaped validation object graph for source-only tests. */
public final class OpsAgentDefinitionValidatorTestFactory {

    private OpsAgentDefinitionValidatorTestFactory() {
    }

    public static OpsAgentDefinitionValidator create() {
        return create(null);
    }

    public static OpsAgentDefinitionValidator create(
            McpClientCatalogPort mcpRepository) {
        return create(mcpRepository, null);
    }

    public static OpsAgentDefinitionValidator create(McpClientCatalogPort mcpRepository,
            cn.lgs.orbisops.application.skill.SkillCatalogPort skillCatalog) {
        OpsAgentToolNamePolicy toolNamePolicy = new OpsAgentToolNamePolicy();
        OpsAgentMcpServerDefinitionPolicy mcpServerPolicy =
                new OpsAgentMcpServerDefinitionPolicy(toolNamePolicy);
        OpsAgentNodeDefinitionPolicy nodePolicy =
                new OpsAgentNodeDefinitionPolicy();
        OpsAgentGraphTopologyPolicy topologyPolicy =
                new OpsAgentGraphTopologyPolicy(nodePolicy);
        OpsAgentGraphDefinitionPolicy graphPolicy =
                new OpsAgentGraphDefinitionPolicy(nodePolicy, topologyPolicy);
        OpsAgentScopeDefinitionPolicy scopePolicy =
                new OpsAgentScopeDefinitionPolicy(toolNamePolicy);
        OpsAgentProjectCapabilityReferenceValidator capabilityValidator =
                new OpsAgentProjectCapabilityReferenceValidator(
                        () -> mcpRepository,
                        () -> skillCatalog,
                        () -> null,
                        () -> null,
                        () -> null,
                        () -> null,
                        () -> null);
        OpsAgentModelKnowledgeReferenceValidator modelKnowledgeValidator =
                new OpsAgentModelKnowledgeReferenceValidator(
                        () -> null,
                        () -> null,
                        () -> null);
        OpsAgentDefinitionResourceValidator resourceValidator =
                new OpsAgentDefinitionResourceValidator(
                        capabilityValidator,
                        modelKnowledgeValidator,
                        mcpServerPolicy);
        return new OpsAgentDefinitionValidator(
                graphPolicy,
                scopePolicy,
                resourceValidator);
    }
}
