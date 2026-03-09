package cn.lgs.orbisops.application.config;

/** Executes the external provider health protocol. */
public interface AiClientApiHealthProbePort {

    AiClientApiHealthProbeOutcome probe(AiClientApiHealthTarget target);
}
