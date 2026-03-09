package cn.lgs.orbisops.trigger.application.statistics;

import cn.lgs.orbisops.application.statistics.DataCatalogStatisticsPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.domain.statistics.model.DataCatalogCounts;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.springframework.stereotype.Component;

@Component
public class OpsDataCatalogStatisticsAdapter implements DataCatalogStatisticsPort {

    private final IAiClientModelConfigRepository models;
    private final IAiClientRagOrderConfigRepository ragOrders;
    private final IAiClientToolMcpConfigRepository mcpTools;
    private final OpsAgentDefinitionQueryGateway agentDefinitions;

    public OpsDataCatalogStatisticsAdapter(
            IAiClientModelConfigRepository models,
            IAiClientRagOrderConfigRepository ragOrders,
            IAiClientToolMcpConfigRepository mcpTools,
            OpsAgentDefinitionQueryGateway agentDefinitions) {
        this.models = models;
        this.ragOrders = ragOrders;
        this.mcpTools = mcpTools;
        this.agentDefinitions = agentDefinitions;
    }

    @Override
    public DataCatalogCounts loadCatalogCounts() {
        return new DataCatalogCounts(
                agentDefinitions.list().size(),
                mcpTools.queryAll().size(),
                ragOrders.queryAll().size(),
                models.queryAll().size());
    }
}
