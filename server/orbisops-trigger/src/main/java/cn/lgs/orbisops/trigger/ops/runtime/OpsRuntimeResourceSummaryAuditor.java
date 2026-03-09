package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;

/** Finalizes runtime resource metadata and emits the assembly audit event. */
@Component
public final class OpsRuntimeResourceSummaryAuditor {

    public void summarize(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        context.getMetadata().put("owner", context.ownerLabel());
        context.getMetadata().put("projectId", value(context.getProjectId()));
        context.getMetadata().put("modelId", value(context.getModelId()));
        context.getMetadata().put("mcpIds", new ArrayList<>(context.getMcpIds()));
        context.getMetadata().put("mcpServerCount", context.getMcpServers().size());
        context.getMetadata().put("skillNames", new ArrayList<>(context.getSkillNames()));
        context.record(OpsRuntimeEvent.builder()
                .eventType("RUNTIME_RESOURCES")
                .status("SUCCEEDED")
                .summary("运行资源装配完成：" + context.ownerLabel())
                .payload(context.getMetadata())
                .build());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
