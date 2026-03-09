package cn.lgs.orbisops.domain.execution.model;

public record ExecutionSourceResource(
        String resourceId,
        String type,
        String environment) {

    public ExecutionSourceResource {
        resourceId = text(resourceId);
        type = text(type);
        environment = text(environment);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
