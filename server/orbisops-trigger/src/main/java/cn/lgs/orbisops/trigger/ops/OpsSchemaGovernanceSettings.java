package cn.lgs.orbisops.trigger.ops;

/** Typed schema auto-initialization state across governed persistence domains. */
public record OpsSchemaGovernanceSettings(
        boolean agentDefinitionAutoInit,
        boolean graphEventsAutoInit,
        boolean runsAutoInit,
        boolean auditAutoInit,
        boolean alertTriggerAutoInit,
        boolean chatMemoryAutoInit,
        boolean ragIngestionAutoInit) {

    public static OpsSchemaGovernanceSettings defaults() {
        return new OpsSchemaGovernanceSettings(
                false, false, false, false, false, false, false);
    }

    public boolean anyAutoInitEnabled() {
        return agentDefinitionAutoInit
                || graphEventsAutoInit
                || runsAutoInit
                || auditAutoInit
                || alertTriggerAutoInit
                || chatMemoryAutoInit
                || ragIngestionAutoInit;
    }
}
