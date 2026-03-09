package cn.lgs.orbisops.application.config;

/** Resolves the operator identity attached to one manual provider health check. */
public interface AiClientApiHealthOperatorPort {

    String currentOperator();
}
