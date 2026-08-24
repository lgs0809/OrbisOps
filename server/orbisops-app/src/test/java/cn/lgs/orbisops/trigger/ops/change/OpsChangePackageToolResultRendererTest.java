package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OpsChangePackageToolResultRendererTest {
    private final OpsChangePackageToolResultRenderer renderer = new OpsChangePackageToolResultRenderer();

    @Test void emptyOrDeniedResultCannotClaimDraftCreation() {
        assertThatThrownBy(() -> renderer.success(Map.of(), 1, 1))
                .hasMessage("CHANGE_PACKAGE_CREATION_NOT_CONFIRMED");
        assertThatThrownBy(() -> renderer.success(Map.of("allowed", false, "packageId", "cp-1",
                "version", 1, "packageHash", "a".repeat(64), "status", "READY_FOR_REVIEW"), 1, 1))
                .hasMessage("CHANGE_PACKAGE_CREATION_NOT_CONFIRMED");
    }

    @Test void compilerLimitationsRemainAvailableForAutomaticCorrection() {
        String result = renderer.success(Map.of("packageId", "cp-1", "version", 1,
                "packageHash", "a".repeat(64), "status", "VALIDATION_FAILED",
                "reasonCode", "INVALID_TYPE", "evidenceJson", "{\"limitations\":[\"TYPE_CONFLICT\"]}"), 2, 1);
        assertThat(result).contains("INVALID_TYPE", "TYPE_CONFLICT", "继续修正");
    }

    @Test void savedButFailedValidationMustNotSuggestSubmittingForApproval() {
        String result = renderer.success(Map.of("packageId", "cp-1", "version", 1,
                "packageHash", "a".repeat(64), "status", "VALIDATION_FAILED"), 2, 1);
        assertThat(result).contains("验证失败", "不能提交审批", "cp-1");
    }
}
