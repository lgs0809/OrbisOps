package cn.lgs.orbisops.application.project;

/** Published-language read port for proving that one project completed a real evidence-backed diagnosis. */
public interface ProjectFirstValueEvidencePort {

    ProjectFirstValueEvidence verification(String projectId);

    record ProjectFirstValueEvidence(
            boolean verified,
            String runId,
            String summary) {

        public ProjectFirstValueEvidence {
            runId = text(runId);
            summary = text(summary);
        }

        public static ProjectFirstValueEvidence none() {
            return new ProjectFirstValueEvidence(false, "", "尚未找到成功的真实证据诊断运行");
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
