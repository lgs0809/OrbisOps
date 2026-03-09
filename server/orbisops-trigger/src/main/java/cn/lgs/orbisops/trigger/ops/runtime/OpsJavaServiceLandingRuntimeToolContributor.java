package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.trigger.ops.toolset.JavaServiceLandingToolsetContributor;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalJavaServiceToolExecutionHandler;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Exposes physical Java artifact deployment only inside the platform-owned Landing runtime.
 * Artifact identity and idempotency are injected from server-owned approved-package authority;
 * the model can select execution details but cannot substitute different artifact bytes.
 */
@Component
public final class OpsJavaServiceLandingRuntimeToolContributor implements OpsRuntimeToolContributor {

    public static final String DEPLOY_TOOL = "DeployApprovedJavaArtifact";
    public static final String ROLLBACK_TOOL = "RollbackPreviousJavaArtifact";

    private final Supplier<OpsToolExecutionService> toolExecutionServiceSupplier;

    @Autowired
    public OpsJavaServiceLandingRuntimeToolContributor(ObjectProvider<OpsToolExecutionService> toolExecutionService) {
        this(toolExecutionService == null ? null : toolExecutionService::getIfAvailable);
    }

    OpsJavaServiceLandingRuntimeToolContributor(Supplier<OpsToolExecutionService> toolExecutionServiceSupplier) {
        if (toolExecutionServiceSupplier == null) {
            throw new IllegalArgumentException("JAVA_LANDING_TOOL_EXECUTION_SERVICE_SUPPLIER_REQUIRED");
        }
        this.toolExecutionServiceSupplier = toolExecutionServiceSupplier;
    }

    @Override
    public String id() {
        return "java-service-landing";
    }

    @Override
    public int order() {
        return 650;
    }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.OPTIONAL;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        if (context.getExecutionContext() == null
                || context.getExecutionContext().stage() != AgentExecutionStage.LANDING) {
            return;
        }
        ApprovedPackageSnapshot approved = context.getExecutionContext().approvedPackage()
                .orElseThrow(() -> new SecurityException("JAVA_LANDING_APPROVED_PACKAGE_REQUIRED"));
        String approvedSha = approvedSha256(approved.artifactDigest());
        if (approvedSha.isBlank()) {
            OpsRuntimeToolContributionSupport.warn(
                    context,
                    "已审批任务没有可验证的 SHA-256 artifactDigest，未暴露 Java 制品部署工具。");
            return;
        }
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(runId)) {
            throw new SecurityException("JAVA_LANDING_RUNTIME_IDENTITY_REQUIRED");
        }

        ToolCallback deploy = deployTool(context.getProjectId(), actor, runId, approved, approvedSha);
        ToolCallback rollback = rollbackTool(context.getProjectId(), actor, runId, approved);
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                deploy,
                OpsRuntimeToolAuthorityDescriptor.targetWrite(
                        "JAVA_ARTIFACT_LANDING", Set.of(AgentExecutionStage.LANDING), Set.of(DEPLOY_TOOL))));
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                rollback,
                OpsRuntimeToolAuthorityDescriptor.targetWrite(
                        "JAVA_ARTIFACT_LANDING", Set.of(AgentExecutionStage.LANDING), Set.of(ROLLBACK_TOOL))));
        context.getMetadata().put("javaArtifactLandingEnabled", true);
        context.getMetadata().put("javaArtifactLandingDigest", "sha256:" + approvedSha);
    }

    private ToolCallback deployTool(
            String projectId,
            String actor,
            String runId,
            ApprovedPackageSnapshot approved,
            String approvedSha) {
        Function<DeployInput, String> function = input -> JSON.toJSONString(
                executeDeploy(projectId, actor, runId, approved, approvedSha, input));
        return FunctionToolCallback.builder(DEPLOY_TOOL, function)
                .description("""
                        Deploy the already-approved Java artifact to one configured LOCAL_JAVA_SERVICE execution resource.
                        Provide executionResourceId, serviceId, and artifactPath. The platform injects the approved SHA-256,
                        ChangePackage identity and idempotency key; supplying another digest is neither required nor allowed.
                        """)
                .inputType(DeployInput.class)
                .build();
    }

    private ToolCallback rollbackTool(
            String projectId,
            String actor,
            String runId,
            ApprovedPackageSnapshot approved) {
        Function<RollbackInput, String> function = input -> JSON.toJSONString(
                executeRollback(projectId, actor, runId, approved, input));
        return FunctionToolCallback.builder(ROLLBACK_TOOL, function)
                .description("""
                        Roll back the same Java service to the exact artifact that was saved immediately before this approved deployment.
                        Provide executionResourceId and serviceId only. The platform derives the original deployment execution key;
                        arbitrary historical artifact paths or hashes cannot be supplied by the model.
                        """)
                .inputType(RollbackInput.class)
                .build();
    }

    private Map<String, Object> executeDeploy(
            String projectId,
            String actor,
            String runId,
            ApprovedPackageSnapshot approved,
            String approvedSha,
            DeployInput input) {
        if (input == null) throw new IllegalArgumentException("JAVA_DEPLOY_INPUT_REQUIRED");
        String resourceId = required(input.getExecutionResourceId(), "EXECUTION_RESOURCE_ID_REQUIRED");
        String serviceId = required(input.getServiceId(), "SERVICE_ID_REQUIRED");
        String artifactPath = required(input.getArtifactPath(), "ARTIFACT_PATH_REQUIRED");
        String executionKey = executionKey(approved, resourceId, serviceId);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", projectId);
        arguments.put("executionResourceId", resourceId);
        arguments.put("serviceId", serviceId);
        arguments.put("artifactPath", artifactPath);
        arguments.put("artifactSha256", approvedSha);
        arguments.put("executionKey", executionKey);
        return executeLanding(projectId, actor, runId, approved,
                OpsLocalJavaServiceToolExecutionHandler.DEPLOY, executionKey, arguments);
    }

    private Map<String, Object> executeRollback(
            String projectId,
            String actor,
            String runId,
            ApprovedPackageSnapshot approved,
            RollbackInput input) {
        if (input == null) throw new IllegalArgumentException("JAVA_ROLLBACK_INPUT_REQUIRED");
        String resourceId = required(input.getExecutionResourceId(), "EXECUTION_RESOURCE_ID_REQUIRED");
        String serviceId = required(input.getServiceId(), "SERVICE_ID_REQUIRED");
        String executionKey = executionKey(approved, resourceId, serviceId);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", projectId);
        arguments.put("executionResourceId", resourceId);
        arguments.put("serviceId", serviceId);
        arguments.put("executionKey", executionKey);
        return executeLanding(projectId, actor, runId, approved,
                OpsLocalJavaServiceToolExecutionHandler.ROLLBACK, executionKey + ":rollback", arguments);
    }

    private Map<String, Object> executeLanding(
            String projectId,
            String actor,
            String runId,
            ApprovedPackageSnapshot approved,
            String toolName,
            String idempotencyKey,
            Map<String, Object> arguments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("userId", actor);
        request.put("runId", runId);
        request.put("toolsetId", JavaServiceLandingToolsetContributor.TOOLSET_ID);
        request.put("toolName", toolName);
        request.put("arguments", Map.copyOf(arguments));
        request.put("idempotencyKey", idempotencyKey);
        request.put("packageId", approved.packageId());
        request.put("packageVersion", approved.packageVersion());
        request.put("packageHash", approved.packageHash());
        request.put("operationId", toolName + ":" + idempotencyKey);
        OpsToolExecutionService service = toolExecutionServiceSupplier.get();
        if (service == null) throw new IllegalStateException("JAVA_LANDING_TOOL_EXECUTION_SERVICE_UNAVAILABLE");
        return service.executeLanding(request, actor);
    }

    private String executionKey(ApprovedPackageSnapshot approved, String resourceId, String serviceId) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("packageId", approved.packageId());
        identity.put("packageVersion", approved.packageVersion());
        identity.put("packageHash", approved.packageHash());
        identity.put("executionResourceId", resourceId);
        identity.put("serviceId", serviceId);
        return "java-landing:" + CanonicalObjectHasher.sha256(identity);
    }

    private String approvedSha256(String digest) {
        String value = digest == null ? "" : digest.trim().toLowerCase();
        if (value.startsWith("sha256:")) value = value.substring("sha256:".length());
        return value.matches("[a-f0-9]{64}") ? value : "";
    }

    private String required(Object value, String code) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    @Data
    public static class DeployInput {
        private String executionResourceId;
        private String serviceId;
        private String artifactPath;
    }

    @Data
    public static class RollbackInput {
        private String executionResourceId;
        private String serviceId;
    }
}
