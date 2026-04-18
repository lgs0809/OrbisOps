package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

/** Append-only independent observations, separate from execution acknowledgements. */
public interface LandingVerificationRecordPort {
    void record(String verificationId, String landingRunId, String projectId, String packageId,
                long approvedVersion, String approvedHash, boolean passed, Map<String, Object> proof);
}
