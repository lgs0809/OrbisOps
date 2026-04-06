package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.api.dto.OpsExecutionResourceRequestDTO;
import cn.lgs.orbisops.application.execution.ExecutionResourceCapabilities;
import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeBinding;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceCapability;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsExecutionResourceMapper {

    private static final DateTimeFormatter DB_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ExecutionResourceDraft draft(OpsExecutionResourceRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("执行资源配置不能为空");
        return new ExecutionResourceDraft(
                request.getResourceId(),
                request.getProjectId(),
                request.getName(),
                request.getWorkerId(),
                request.getAdapter(),
                request.getAdapterTemplateId(),
                request.getEnvironments(),
                request.getConfiguration(),
                request.getStatus());
    }

    public ExecutionResourceDraft draft(
            String projectId,
            String resourceId,
            OpsExecutionResourceRequestDTO request) {
        ExecutionResourceDraft source = draft(request);
        return new ExecutionResourceDraft(
                resourceId,
                projectId,
                source.name(),
                source.workerId(),
                source.adapter(),
                source.adapterTemplateId(),
                source.environments(),
                source.configuration(),
                source.status());
    }

    public OpsExecutionResourceDTO dto(ExecutionResource resource) {
        if (resource == null) return null;
        return OpsExecutionResourceDTO.builder()
                .resourceId(resource.resourceId())
                .projectId(resource.projectId())
                .name(resource.name())
                .workerId(resource.workerId())
                .adapter(resource.adapter().code())
                .adapterTemplateId(resource.adapterTemplateId())
                .environments(new ArrayList<>(resource.environments()))
                .configuration(new LinkedHashMap<>(resource.configuration()))
                .status(resource.status().name())
                .createdAt(time(resource.createdAt()))
                .updatedAt(time(resource.updatedAt()))
                .build();
    }

    public Map<String, Object> capabilities(ExecutionResourceCapabilities capabilities) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", capabilities.enabled());
        result.put("adapters", capabilities.adapters());
        result.put("resourceCount", capabilities.resourceCount());
        result.put("arbitraryShellAllowed", capabilities.arbitraryShellAllowed());
        result.put("dynamicWorkerConfig", capabilities.dynamicWorkerConfig());
        return result;
    }

    public Map<String, Object> proposal(ExecutionResourceCapability capability) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", capability.resourceId());
        result.put("name", capability.name());
        result.put("adapter", capability.adapter().code());
        result.put("environments", capability.environments());
        result.put("allowedActions", capability.allowedActions());
        if (!capability.targetObjects().isEmpty()) {
            result.put("targetObjects", capability.targetObjects());
        }
        result.putAll(capability.constraints());
        return result;
    }

    public Map<String, Object> runtime(ExecutionResourceRuntimeBinding binding) {
        Map<String, Object> result = new LinkedHashMap<>(binding.configuration());
        result.put("adapter", binding.adapter().code());
        result.put("environments", new ArrayList<>(binding.environments()));
        result.put("projectId", binding.projectId());
        return result;
    }

    public List<OpsExecutionResourceDTO> dtoList(List<ExecutionResource> resources) {
        return resources == null ? List.of() : resources.stream().map(this::dto).toList();
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : DB_TIME.format(value);
    }
}
