package cn.lgs.orbisops.application.project;

/**
 * Read port for the durable acceptance fact produced by a production-like
 * emergency-stop restart drill.  The project application service consumes only
 * this narrow published-language fact and does not depend on the audit
 * bounded context directly.
 */
public interface ProjectEmergencyStopAcceptancePort {

    EmergencyStopAcceptanceFact acceptance(String projectId);

    record EmergencyStopAcceptanceFact(boolean accepted, String detail) {

        public EmergencyStopAcceptanceFact {
            detail = detail == null ? "" : detail.trim();
        }

        public static EmergencyStopAcceptanceFact notValidated() {
            return new EmergencyStopAcceptanceFact(
                    false,
                    "急停治理能力已配置，但尚未接入持久化环境验收事实投影");
        }
    }
}
