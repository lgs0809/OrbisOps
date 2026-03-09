package cn.lgs.orbisops.trigger.ops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable public projection of schema governance state and migration catalog. */
final class OpsSchemaGovernanceSnapshotFactory {

    private static final List<String> MIGRATION_FILES = List.of(
            "db/migrations/sql/orbisops.sql",
            "db/migrations/sql/ops-agent-runtime.sql",
            "db/migrations/sql/ops-production-hardening.sql",
            "db/migrations/sql/ops-agent-run.sql",
            "db/migrations/sql/ops-agent-audit.sql",
            "db/migrations/sql/admin-auth.sql");

    Map<String, Object> create(OpsSchemaGovernanceSettings settings) {
        OpsSchemaGovernanceSettings resolved = settings == null
                ? OpsSchemaGovernanceSettings.defaults()
                : settings;
        Map<String, Boolean> autoInit = new LinkedHashMap<>();
        autoInit.put("agentDefinitions", resolved.agentDefinitionAutoInit());
        autoInit.put("graphEvents", resolved.graphEventsAutoInit());
        autoInit.put("runs", resolved.runsAutoInit());
        autoInit.put("audit", resolved.auditAutoInit());
        autoInit.put("alertTriggers", resolved.alertTriggerAutoInit());
        autoInit.put("chatMemory", resolved.chatMemoryAutoInit());
        autoInit.put("ragIngestion", resolved.ragIngestionAutoInit());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ddlMode", resolved.anyAutoInitEnabled()
                ? "AUTO_INIT_ENABLED"
                : "MANUAL_MIGRATION_REQUIRED");
        data.put("autoInit", autoInit);
        data.put("migrationFiles", MIGRATION_FILES);
        return data;
    }
}
