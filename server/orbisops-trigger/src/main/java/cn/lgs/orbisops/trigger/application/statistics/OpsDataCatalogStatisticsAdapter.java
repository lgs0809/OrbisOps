package cn.lgs.orbisops.trigger.application.statistics;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.statistics.DataCatalogStatisticsPort;
import cn.lgs.orbisops.domain.statistics.model.DataCatalogCounts;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.springframework.stereotype.Component;

@Component
public class OpsDataCatalogStatisticsAdapter implements DataCatalogStatisticsPort {

    private final AiClientModelCatalogPort models;
    private final RagOrderCatalogPort ragOrders;
    private final McpClientCatalogPort mcpTools;
    private final OpsAgentDefinitionQueryGateway agentDefinitions;

    public OpsDataCatalogStatisticsAdapter(
            AiClientModelCatalogPort models,
            RagOrderCatalogPort ragOrders,
            McpClientCatalogPort mcpTools,
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
                mcpTools.listAll().size(),
                ragOrders.queryAll().size(),
                models.listAll().size());
    }
}
