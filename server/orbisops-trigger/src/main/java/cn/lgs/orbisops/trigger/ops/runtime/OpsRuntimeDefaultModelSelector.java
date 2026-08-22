package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicySnapshot;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelPolicyStatus;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/** Resolves project/platform default model policy before runtime model construction. */
final class OpsRuntimeDefaultModelSelector {

    private final Supplier<ModelDefaultPolicyApplicationService> policySupplier;

    OpsRuntimeDefaultModelSelector(Supplier<ModelDefaultPolicyApplicationService> policySupplier) {
        if (policySupplier == null) {
            throw new IllegalArgumentException("RUNTIME_MODEL_DEFAULT_POLICY_SUPPLIER_REQUIRED");
        }
        this.policySupplier = policySupplier;
    }

    String resolve(OpsRuntimeResourceContext context) {
        ModelDefaultPolicyApplicationService policies = policySupplier.get();
        if (policies == null) return "";
        String projectId = context == null ? "" : text(context.getProjectId());
        ModelDefaultPolicySnapshot snapshot = policies.find(projectId).orElse(null);
        String source = "PROJECT_DEFAULT";
        if (snapshot == null && StringUtils.hasText(projectId)) {
            snapshot = policies.find("").orElse(null);
            source = "PLATFORM_DEFAULT";
        }
        if (snapshot == null
                || snapshot.policy().status() != ModelPolicyStatus.ENABLED
                || !StringUtils.hasText(snapshot.policy().chatModelId())) {
            return "";
        }
        String modelId = snapshot.policy().chatModelId().trim();
        if (context != null) {
            context.getMetadata().put("modelSelectionSource", source);
            context.getMetadata().put("defaultModelId", modelId);
        }
        return modelId;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
