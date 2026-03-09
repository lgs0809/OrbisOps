package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteApplicationService;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteDraft;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteSnapshot;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administrative publication and lookup API for versioned strict-tournament suites. */
@RestController
@RequestMapping("/api/v1/admin/ops/skills/{skillId}/hidden-eval-suites")
public final class OpsSkillHiddenEvaluationSuiteAdminController {

    private final SkillHiddenEvaluationSuiteApplicationService service;

    public OpsSkillHiddenEvaluationSuiteAdminController(
            SkillHiddenEvaluationSuiteApplicationService service) {
        if (service == null) {
            throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_SERVICE_REQUIRED");
        }
        this.service = service;
    }

    @PostMapping
    public Response<Map<String, Object>> publish(
            @PathVariable("skillId") String skillId,
            @RequestBody PublishRequest request,
            Principal principal) {
        if (request == null) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_REQUEST_REQUIRED");
        SkillHiddenEvaluationSuiteSnapshot snapshot = service.publish(
                new SkillHiddenEvaluationSuiteDraft(
                        request.projectId(),
                        required(skillId, "SKILL_HIDDEN_SUITE_SKILL_ID_REQUIRED"),
                        request.baseVersion(),
                        request.baseSkillHash(),
                        request.suiteVersion(),
                        request.hiddenCases(),
                        request.mutationCases(),
                        actor(principal)));
        return success(view(snapshot));
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list(
            @PathVariable("skillId") String skillId,
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return success(service.list(
                        projectId,
                        skillId,
                        limit == null ? 50 : limit).stream()
                .map(this::view)
                .toList());
    }

    @GetMapping("/{suiteVersion}")
    public Response<Map<String, Object>> get(
            @PathVariable("skillId") String skillId,
            @PathVariable("suiteVersion") String suiteVersion,
            @RequestParam("projectId") String projectId,
            @RequestParam("baseVersion") Long baseVersion,
            @RequestParam("baseSkillHash") String baseSkillHash) {
        return success(view(service.require(
                projectId,
                skillId,
                baseVersion == null ? 0L : baseVersion,
                baseSkillHash,
                suiteVersion)));
    }

    private Map<String, Object> view(SkillHiddenEvaluationSuiteSnapshot snapshot) {
        return Map.ofEntries(
                Map.entry("suiteId", snapshot.suiteId()),
                Map.entry("projectId", snapshot.projectId()),
                Map.entry("skillId", snapshot.skillId()),
                Map.entry("baseVersion", snapshot.baseVersion()),
                Map.entry("baseSkillHash", snapshot.baseSkillHash()),
                Map.entry("suiteVersion", snapshot.suiteVersion()),
                Map.entry("hiddenEvalHash", snapshot.hiddenEvalHash()),
                Map.entry("mutationEvalHash", snapshot.mutationEvalHash()),
                Map.entry("status", snapshot.status()),
                Map.entry("actor", snapshot.actor()),
                Map.entry("createdAt", snapshot.createdAt().toString()),
                Map.entry("updatedAt", snapshot.updatedAt().toString()));
    }

    private String actor(Principal principal) {
        String value = principal == null ? "" : principal.getName();
        return required(value, "SKILL_HIDDEN_SUITE_AUTHENTICATED_ACTOR_REQUIRED");
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    public record PublishRequest(
            String projectId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion,
            List<Map<String, Object>> hiddenCases,
            List<Map<String, Object>> mutationCases) {

        public PublishRequest {
            projectId = projectId == null ? "" : projectId.trim();
            baseSkillHash = baseSkillHash == null ? "" : baseSkillHash.trim();
            suiteVersion = suiteVersion == null ? "" : suiteVersion.trim();
            hiddenCases = maps(hiddenCases);
            mutationCases = maps(mutationCases);
        }

        private static List<Map<String, Object>> maps(
                List<Map<String, Object>> source) {
            if (source == null || source.isEmpty()) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : source) {
                result.add(item == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(item)));
            }
            return List.copyOf(result);
        }
    }
}
