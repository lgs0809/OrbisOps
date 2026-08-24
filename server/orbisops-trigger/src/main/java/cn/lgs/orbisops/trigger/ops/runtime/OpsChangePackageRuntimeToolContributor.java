package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
public final class OpsChangePackageRuntimeToolContributor implements OpsRuntimeToolContributor {

    static final String GOVERNANCE_PHASE_KEY = "_changePackageGovernancePhase";
    public static final String AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY = "_changePackageAvailableAuthoritativeSourceTypes";
    /** In-memory hand-off of prior same-session evidence into the governed PREPARE tool. */
    public static final String SESSION_EVIDENCE_KEY = "_changePackageSessionEvidence";

    private final Supplier<OpsChangePackageToolProvider> providerSupplier;

    @Autowired
    public OpsChangePackageRuntimeToolContributor(
            ObjectProvider<OpsChangePackageToolProvider> provider) {
        this(provider::getIfAvailable);
    }

    OpsChangePackageRuntimeToolContributor(Supplier<OpsChangePackageToolProvider> providerSupplier) {
        if (providerSupplier == null) throw new IllegalArgumentException("CHANGE_PACKAGE_TOOL_SUPPLIER_REQUIRED");
        this.providerSupplier = providerSupplier;
    }

    @Override
    public String id() { return "change-package"; }

    @Override
    public int order() { return 200; }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        OpsAgentChatRequest request = context.getRequest();
        Map<String, Object> requestMetadata = request == null || request.getMetadata() == null
                ? Map.of()
                : request.getMetadata();
        boolean explicitGovernancePhase = Boolean.TRUE.equals(requestMetadata.get(GOVERNANCE_PHASE_KEY));
        boolean reactPrepareAuthority = OpsRuntimeAgentAuthority.resolve(context)
                == OpsRuntimeAgentAuthority.PREPARE_CHANGE;
        if (!explicitGovernancePhase && !reactPrepareAuthority) {
            return;
        }
        OpsChangePackageToolProvider provider = providerSupplier.get();
        if (provider == null
                || !Boolean.TRUE.equals(context.getChangePackageEnabled())
                || !StringUtils.hasText(context.getProjectId())) {
            return;
        }
        List<String> executionTargetIds = new ArrayList<>(context.getExecutionTargetIds());
        if (!provider.available(context.getProjectId(), executionTargetIds)) {
            OpsRuntimeToolContributionSupport.warn(
                    context, "受控变更未启用、未绑定执行目标，或绑定目标不可用，未暴露提案工具。");
            return;
        }
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(actor)) {
            OpsRuntimeToolContributionSupport.warn(
                    context, "受控变更提案工具缺少 runId 或真实操作者，未启用。");
            return;
        }
        Map<String, Object> metadata = context.getRequest() == null
                || context.getRequest().getMetadata() == null
                ? Map.of()
                : context.getRequest().getMetadata();
        if (request != null) {
            if (request.getMetadata() == null) request.setMetadata(new LinkedHashMap<>());
            request.getMetadata().put(
                    AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY,
                    availableAuthoritativeSourceTypes(context));
            metadata = request.getMetadata();
        }
        ToolCallback tool = provider.build(
                context.getProjectId(),
                actor,
                runId,
                OpsRuntimeToolContributionSupport.stringValue(metadata.get("contextBundleId")),
                OpsRuntimeToolContributionSupport.stringValue(metadata.get("contextBundleHash")),
                context.getEvents(),
                executionTargetIds,
                context.getRequest());
        if (tool == null) {
            throw new IllegalStateException(
                    "ChangePackage ToolProvider 未返回受控工具，Work Session 已阻断");
        }
        String toolName = tool.getToolDefinition() == null || tool.getToolDefinition().name() == null
                ? ""
                : tool.getToolDefinition().name().trim();
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                tool,
                OpsRuntimeToolAuthorityDescriptor.changePackage(
                        "CHANGE_PACKAGE",
                        Set.of(AgentExecutionStage.PREPARE),
                        Set.of(toolName))));
        ToolCallback statusQuery = provider.buildStatusQuery(context.getProjectId(), actor, runId, request);
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(statusQuery,
                OpsRuntimeToolAuthorityDescriptor.readOnly("CHANGE_PACKAGE",
                        Set.of(AgentExecutionStage.PREPARE), Set.of("QueryChangePackageStatus"))));
        context.getMetadata().put("changePackageToolEnabled", true);
        context.getMetadata().put("changePackageRunId", runId);
        context.getMetadata().put("executionTargetIds", executionTargetIds);
    }

    private List<String> availableAuthoritativeSourceTypes(OpsRuntimeResourceContext context) {
        if (context == null || context.getTools() == null) return List.of();
        return context.getTools().stream()
                .map(callback -> callback == null || callback.getToolDefinition() == null
                        ? ""
                        : callback.getToolDefinition().name())
                .map(this::sourceType)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String sourceType(String toolName) {
        String name = toolName == null ? "" : toolName.trim().toLowerCase();
        if (name.startsWith("mcp_tool_catalog_") || name.startsWith("enable_mcp_tool_")) return "";
        if (OpsDatasourceRuntimeToolProvider.PROMETHEUS_TOOL.equals(name)
                || name.contains("prometheus")) return "PROMETHEUS";
        if (OpsDatasourceRuntimeToolProvider.ELASTICSEARCH_TOOL.equals(name)
                || name.contains("elasticsearch")) return "ELASTICSEARCH";
        if (name.contains("rabbitmq")) return "RABBITMQ";
        if (name.contains("redis")) return "REDIS";
        if (name.contains("service-control") || name.contains("service_control")
                || "get_service_status".equals(name)
                || "restart_service_dry_run".equals(name)
                || "restart_service".equals(name)
                || "get_operation_receipt".equals(name)) return "SERVICE_CONTROL";
        if (name.contains("mysql") && (name.contains("slow") || name.contains("digest"))) {
            return "MYSQL_SLOW_SQL";
        }
        if (name.contains("mysql")) return "MYSQL";
        return "";
    }
}
