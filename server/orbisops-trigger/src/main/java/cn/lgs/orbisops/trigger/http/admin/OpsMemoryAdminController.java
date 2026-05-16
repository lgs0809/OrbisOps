package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextMemoryService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryItem;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import cn.lgs.orbisops.trigger.application.runtime.OpsTaskContextAdapter;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsMemoryAdminController {

    private final OpsContextMemoryService contextMemoryService;
    private final OpsTaskContextAdapter taskContextService;
    private final OpsMemoryExtractor memoryExtractor;
    private final OpsConfigAuditService auditService;

    public OpsMemoryAdminController(OpsContextMemoryService contextMemoryService,
                                    OpsTaskContextAdapter taskContextService,
                                    OpsMemoryExtractor memoryExtractor,
                                    OpsConfigAuditService auditService) {
        this.contextMemoryService = contextMemoryService;
        this.taskContextService = taskContextService;
        this.memoryExtractor = memoryExtractor;
        this.auditService = auditService;
    }

    @GetMapping("/context-memories")
    public Response<List<Map<String, Object>>> listContextMemories(
            @RequestParam(value = "scopeType", required = false) String scopeType,
            @RequestParam(value = "scopeId", required = false) String scopeId,
            @RequestParam(value = "memoryType", required = false) String memoryType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(contextMemoryService.list(scopeType, scopeId, memoryType, status, limit == null ? 100 : limit));
    }

    @PostMapping("/context-memories")
    public Response<Map<String, Object>> createContextMemory(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> data = contextMemoryService.create(safe(request));
        auditService.record(
                projectId(data),
                "context-memory",
                "create",
                String.valueOf(data.get("memoryId")),
                null,
                data);
        return success(data);
    }

    @PutMapping("/context-memories/{memoryId}")
    public Response<Map<String, Object>> updateContextMemory(@PathVariable("memoryId") String memoryId,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> before = contextMemoryService.get(memoryId);
        Map<String, Object> data = contextMemoryService.update(memoryId, safe(request));
        auditService.record(projectId(data), "context-memory", "update", memoryId, before, data);
        return success(data);
    }

    @PatchMapping("/context-memories/{memoryId}/status")
    public Response<Map<String, Object>> updateContextMemoryStatus(@PathVariable("memoryId") String memoryId,
                                                                   @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> before = contextMemoryService.get(memoryId);
        String status = String.valueOf(safe(request).getOrDefault("status", "ARCHIVED"));
        Map<String, Object> data = contextMemoryService.updateStatus(memoryId, status);
        auditService.record(projectId(data), "context-memory", "status", memoryId, before, data);
        return success(data);
    }

    @PostMapping("/context-memories/extract-candidates")
    public Response<List<Map<String, Object>>> extractCandidates(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = safe(request);
        String content = String.valueOf(body.getOrDefault("content", body.getOrDefault("text", "")));
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("抽取候选 Memory 必须提供 content");
        }
        OpsMemoryMessage message = OpsMemoryMessage.builder()
                .sessionId(String.valueOf(body.getOrDefault("sessionId", "")))
                .userId(String.valueOf(body.getOrDefault("userId", "")))
                .role(String.valueOf(body.getOrDefault("role", "user")))
                .content(content)
                .metadata(safeMap(body.get("metadata")))
                .build();
        List<Map<String, Object>> candidates = memoryExtractor.extract(message).stream()
                .map(this::candidateView)
                .toList();
        return success(candidates);
    }

    @GetMapping("/runs/{runId}/task-context")
    public Response<Map<String, Object>> getTaskContext(@PathVariable("runId") String runId) {
        return success(taskContextService.get(runId));
    }

    private Map<String, Object> candidateView(OpsMemoryItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memoryType", item.getMemoryType());
        data.put("content", item.getContent());
        data.put("confidence", item.getImportance());
        data.put("tags", item.getTagsJson());
        data.put("metadata", item.getMetadata());
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private String projectId(Map<String, Object> data) {
        return "PROJECT".equals(String.valueOf(data.get("scopeType"))) ? String.valueOf(data.get("scopeId")) : "";
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
