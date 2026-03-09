package cn.lgs.orbisops.application.config;

/** Outcome of the optional default model API bootstrap. */
public record DefaultModelApiBootstrapResult(
        Action action,
        String apiId,
        String baseUrl) {

    public enum Action {
        CREATED,
        MIGRATED,
        SKIPPED
    }
}
