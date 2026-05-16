package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSchemaGovernanceServiceTest {

    @Test
    void noArgCompatibilityConstructorRequiresManualMigration() {
        Map<String, Object> snapshot = new OpsSchemaGovernanceService().snapshot();

        assertEquals("MANUAL_MIGRATION_REQUIRED", snapshot.get("ddlMode"));
        assertFalse(castAutoInit(snapshot).values().stream().anyMatch(Boolean.TRUE::equals));
        assertEquals(6, castMigrationFiles(snapshot).size());
    }

    @Test
    void reportsAutoInitWhenAnyGovernedDomainEnablesIt() {
        OpsSchemaGovernanceService service = new OpsSchemaGovernanceService(
                new OpsSchemaGovernanceSettings(
                        false, false, true, false, false, false, false));

        Map<String, Object> snapshot = service.snapshot();

        assertEquals("AUTO_INIT_ENABLED", snapshot.get("ddlMode"));
        assertTrue(castAutoInit(snapshot).get("runs"));
        assertFalse(castAutoInit(snapshot).get("audit"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> castAutoInit(Map<String, Object> snapshot) {
        return (Map<String, Boolean>) snapshot.get("autoInit");
    }

    @SuppressWarnings("unchecked")
    private List<String> castMigrationFiles(Map<String, Object> snapshot) {
        return (List<String>) snapshot.get("migrationFiles");
    }
}
