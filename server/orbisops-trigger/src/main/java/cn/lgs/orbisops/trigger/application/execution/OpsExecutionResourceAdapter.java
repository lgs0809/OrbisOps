package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.api.dto.OpsExecutionResourceRequestDTO;
import cn.lgs.orbisops.application.execution.ExecutionResourceCommandApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpsExecutionResourceAdapter {

    private final ExecutionResourceCommandApplicationService commands;
    private final ExecutionResourceQueryApplicationService queries;
    private final OpsExecutionResourceMapper mapper;

    public OpsExecutionResourceAdapter(
            ExecutionResourceCommandApplicationService commands,
            ExecutionResourceQueryApplicationService queries,
            OpsExecutionResourceMapper mapper) {
        if (commands == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_COMMANDS_REQUIRED");
        if (queries == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_QUERIES_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_MAPPER_REQUIRED");
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
    }

    public Map<String, Object> capabilities() {
        return mapper.capabilities(queries.capabilities());
    }

    public List<OpsExecutionResourceDTO> list(String projectId) {
        return mapper.dtoList(queries.list(projectId));
    }

    public Optional<OpsExecutionResourceDTO> get(String projectId, String resourceId) {
        return queries.find(projectId, resourceId).map(mapper::dto);
    }

    public OpsExecutionResourceDTO upsert(
            OpsExecutionResourceRequestDTO request,
            String actor) {
        return mapper.dto(commands.upsert(mapper.draft(request), actor));
    }

    public OpsExecutionResourceDTO upsertForIdentity(
            String projectId,
            String resourceId,
            OpsExecutionResourceRequestDTO request,
            String actor) {
        return mapper.dto(commands.upsert(
                mapper.draft(projectId, resourceId, request), actor));
    }

    public OpsExecutionResourceDTO updateStatus(
            String projectId,
            String resourceId,
            String status,
            String actor) {
        return mapper.dto(commands.updateStatus(projectId, resourceId, status, actor));
    }

    public List<Map<String, Object>> proposalCatalog(String projectId) {
        return queries.proposalCatalog(projectId).stream().map(mapper::proposal).toList();
    }

    public List<Map<String, Object>> proposalCatalog(
            String projectId,
            Collection<String> allowedResourceIds) {
        return queries.proposalCatalog(projectId, allowedResourceIds).stream()
                .map(mapper::proposal)
                .toList();
    }

    public List<OpsExecutionResourceDTO> listByTemplate(String templateId) {
        return mapper.dtoList(queries.listByTemplate(templateId));
    }

    public boolean supportsService(String projectId, String resourceId, String serviceId) {
        return queries.supportsService(projectId, resourceId, serviceId);
    }

    public Map<String, Object> workerResources(String workerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        queries.workerResources(workerId)
                .forEach((resourceId, binding) -> result.put(resourceId, mapper.runtime(binding)));
        return result;
    }
}
