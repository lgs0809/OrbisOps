package cn.lgs.orbisops.trigger.ops.runtime;

/** Narrow semantic-memory retrieval policy derived from the shared memory runtime settings. */
public record OpsSemanticMemoryRetrievalSettings(
        int semanticTopK,
        boolean recencyAwareEnabled,
        double recencyHalfLifeTurns) {

    public OpsSemanticMemoryRetrievalSettings {
        semanticTopK = semanticTopK < 0 || semanticTopK > 1_000 ? 8 : semanticTopK;
        recencyHalfLifeTurns = Double.isFinite(recencyHalfLifeTurns)
                && recencyHalfLifeTurns > 0D
                ? recencyHalfLifeTurns
                : 6D;
    }

    public static OpsSemanticMemoryRetrievalSettings from(OpsMemoryFacadeSettings settings) {
        OpsMemoryFacadeSettings resolved = settings == null
                ? OpsMemoryFacadeSettings.defaults()
                : settings;
        return new OpsSemanticMemoryRetrievalSettings(
                resolved.semanticTopK(),
                resolved.recencyAwareEnabled(),
                resolved.recencyHalfLifeTurns());
    }
}
