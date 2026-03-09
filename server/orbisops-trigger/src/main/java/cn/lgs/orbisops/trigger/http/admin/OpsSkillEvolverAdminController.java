package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/skill-evolver")
public class OpsSkillEvolverAdminController {

    private final OpsSkillEvolutionService skillEvolutionService;

    public OpsSkillEvolverAdminController(OpsSkillEvolutionService skillEvolutionService) {
        this.skillEvolutionService = skillEvolutionService;
    }

    @GetMapping("/jobs")
    public Response<List<Map<String, Object>>> listJobs(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(skillEvolutionService.listJobs(status, limit == null ? 100 : limit));
    }

    @GetMapping("/jobs/{jobId}")
    public Response<Map<String, Object>> getJob(@PathVariable("jobId") String jobId) {
        return success(skillEvolutionService.getJob(jobId));
    }

    @PostMapping("/jobs")
    public Response<Map<String, Object>> createJob(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return success(skillEvolutionService.enqueue(
                String.valueOf(safe.getOrDefault("runId", "")),
                String.valueOf(safe.getOrDefault("sessionId", "")),
                String.valueOf(safe.getOrDefault("projectId", "")),
                String.valueOf(safe.getOrDefault("agentId", "")),
                String.valueOf(safe.getOrDefault("triggerReason", "MANUAL"))));
    }

    @PostMapping("/jobs/run-once")
    public Response<List<Map<String, Object>>> runOnce() {
        return success(skillEvolutionService.runBatch());
    }

    @GetMapping("/patches")
    public Response<List<Map<String, Object>>> listPatches(
            @RequestParam(value = "jobId", required = false) String jobId,
            @RequestParam(value = "decision", required = false) String decision,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(skillEvolutionService.listPatches(jobId, decision, limit == null ? 100 : limit));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
