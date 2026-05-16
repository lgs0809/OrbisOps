package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogReader;
import cn.lgs.orbisops.trigger.application.ops.OpsAgentDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsProductMetricsService;
import cn.lgs.orbisops.trigger.ops.OpsSchemaGovernanceService;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpInvocationResiliencePolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRuntimeSloTelemetry;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维运行时观测接口。
 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsTelemetryAdminController {

    @Value("${orbisops.elasticsearch-url:http://127.0.0.1:9200}")
    private String elasticsearchUrl;

    @Value("${orbisops.prometheus-url:http://127.0.0.1:9090}")
    private String prometheusUrl;

    private final OpsTelemetryService opsTelemetryService;
    private final ObjectProvider<SkillCatalogReader> skillToolProvider;
    private final OpsAgentDefinitionApplicationService opsAgentDefinitionApplicationService;
    private final OpsChatApplicationService opsChatApplicationService;
    private final OpsChannelNotificationService channelNotificationService;
    private final OpsSchemaGovernanceService opsSchemaGovernanceService;
    private final OpsMcpRuntimeSloTelemetry mcpSloTelemetry;
    private final OpsMcpInvocationResiliencePolicy mcpResilience;
    private final OpsProductMetricsService productMetricsService;

    public OpsTelemetryAdminController(OpsTelemetryService opsTelemetryService,
                                       ObjectProvider<SkillCatalogReader> skillToolProvider,
                                       OpsAgentDefinitionApplicationService opsAgentDefinitionApplicationService,
                                       OpsChatApplicationService opsChatApplicationService,
                                       OpsChannelNotificationService channelNotificationService,
                                       OpsSchemaGovernanceService opsSchemaGovernanceService,
                                       OpsMcpRuntimeSloTelemetry mcpSloTelemetry,
                                       OpsMcpInvocationResiliencePolicy mcpResilience,
                                       OpsProductMetricsService productMetricsService) {
        this.opsTelemetryService = opsTelemetryService;
        this.skillToolProvider = skillToolProvider;
        this.opsAgentDefinitionApplicationService = opsAgentDefinitionApplicationService;
        this.opsChatApplicationService = opsChatApplicationService;
        this.channelNotificationService = channelNotificationService;
        this.opsSchemaGovernanceService = opsSchemaGovernanceService;
        this.mcpSloTelemetry = mcpSloTelemetry;
        this.mcpResilience = mcpResilience;
        this.productMetricsService = productMetricsService;
    }

    @GetMapping("/product-metrics")
    public Response<Map<String, Object>> productMetrics() {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(productMetricsService.snapshot())
                .build();
    }

    @GetMapping("/telemetry")
    public Response<Map<String, Object>> telemetry() {
        Map<String, Object> data = new LinkedHashMap<>(opsTelemetryService.snapshot());
        data.put("skills", skillSummaries());
        data.put("agentDefinitions", opsAgentDefinitionApplicationService.listAgents());
        data.put("elasticsearchUrl", elasticsearchUrl);
        data.put("prometheusUrl", prometheusUrl);
        data.put("channelNotification", channelNotificationService.statusAll());
        data.put("aiModel", opsChatApplicationService.modelStatus());
        data.put("agentRuntime", opsChatApplicationService.runtimeCapabilities());
        data.put("schemaGovernance", opsSchemaGovernanceService.snapshot());
        data.put("mcpRuntimeSlo", mcpSloTelemetry.snapshot());
        data.put("mcpResilience", mcpResilience.snapshot());
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private List<?> skillSummaries() {
        SkillCatalogReader provider = skillToolProvider.getIfAvailable();
        return provider == null ? List.of() : provider.listSkillSummaries();
    }
}
