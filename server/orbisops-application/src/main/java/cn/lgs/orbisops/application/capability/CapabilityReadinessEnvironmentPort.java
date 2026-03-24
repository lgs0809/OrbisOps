package cn.lgs.orbisops.application.capability;

/** External environment boundary for capability readiness inspection. */
public interface CapabilityReadinessEnvironmentPort {

    CapabilityReadinessEnvironment inspect();
}
