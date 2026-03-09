package cn.lgs.orbisops.application.resourcehealth;

/** Stable capability catalog entry presented by the resource health facade. */
public record ResourceCapability(
        String id,
        String name,
        String accessMode,
        String dataShape) {

    public ResourceCapability {
        id = text(id);
        name = text(name);
        accessMode = text(accessMode);
        dataShape = text(dataShape);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
