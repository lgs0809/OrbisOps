package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationOperations;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Controlled administrative entry point for dry-run and bounded platform migrations. */
@RestController
@RequestMapping("/api/v1/admin/ops/platform-migrations")
public final class OpsPlatformMigrationAdminController {

    private final PlatformCapabilityMigrationOperations operations;

    public OpsPlatformMigrationAdminController(
            PlatformCapabilityMigrationOperations operations) {
        if (operations == null) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_OPERATIONS_REQUIRED");
        }
        this.operations = operations;
    }

    @PostMapping
    public Response<Map<String, Object>> execute(
            @RequestBody(required = false) MigrationRequest request,
            Principal principal) {
        MigrationRequest safe = request == null
                ? new MigrationRequest("", true, 500, List.of())
                : request;
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                safe.migrationId(),
                actor(principal),
                safe.dryRun() == null || safe.dryRun(),
                safe.batchSize() == null ? 500 : safe.batchSize(),
                steps(safe.steps()));
        return success(operations.view(operations.execute(command)));
    }

    @GetMapping("/{migrationId}")
    public Response<Map<String, Object>> get(
            @PathVariable("migrationId") String migrationId) {
        return success(operations.view(operations.require(migrationId)));
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list(
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        int bounded = limit == null ? 50 : limit;
        return success(operations.list(bounded).stream()
                .map(operations::view)
                .toList());
    }

    @GetMapping("/{migrationId}/release-readiness")
    public Response<Map<String, Object>> releaseReadiness(
            @PathVariable("migrationId") String migrationId) {
        return success(operations.releaseReadiness(migrationId).view());
    }

    private List<PlatformCapabilityMigrationStep> steps(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<PlatformCapabilityMigrationStep> result = new ArrayList<>();
        for (String value : values) {
            String normalized = value == null
                    ? ""
                    : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.isBlank()) continue;
            result.add(PlatformCapabilityMigrationStep.valueOf(normalized));
        }
        return List.copyOf(result);
    }

    private String actor(Principal principal) {
        String actor = principal == null ? "" : principal.getName();
        if (actor == null || actor.isBlank()) {
            throw new SecurityException("PLATFORM_MIGRATION_AUTHENTICATED_ACTOR_REQUIRED");
        }
        return actor.trim();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    public record MigrationRequest(
            String migrationId,
            Boolean dryRun,
            Integer batchSize,
            List<String> steps) {

        public MigrationRequest {
            migrationId = migrationId == null ? "" : migrationId.trim();
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }
}
