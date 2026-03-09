package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageCurrentStateJdbcMapperTest {

    @Test
    void ordersValuesByDomainFieldOrderAndPreservesSqlNulls() {
        ChangePackageCurrentState state = ChangePackageCurrentState.fromSnapshot(Map.of(
                "objective", "repair checkout",
                "riskLevel", "HIGH",
                "branchName", "ops/repair/cp-1"));

        List<Object> values = ChangePackageCurrentStateJdbcMapper.orderedValues(state);

        assertEquals(ChangePackageCurrentField.values().length, values.size());
        assertEquals("repair checkout", values.get(ChangePackageCurrentField.OBJECTIVE.ordinal()));
        assertEquals("HIGH", values.get(ChangePackageCurrentField.RISK_LEVEL.ordinal()));
        assertEquals("ops/repair/cp-1", values.get(ChangePackageCurrentField.BRANCH_NAME.ordinal()));
        assertNull(values.get(ChangePackageCurrentField.BASE_BRANCH.ordinal()));
        assertThrows(UnsupportedOperationException.class, () -> values.add("drift"));
    }

    @Test
    void derivesColumnsAssignmentsAndPlaceholdersFromOneFieldCatalog() {
        String[] columns = ChangePackageCurrentStateJdbcMapper.columnList().split(", ");
        String[] assignments = ChangePackageCurrentStateJdbcMapper.assignmentList().split(", ");

        assertEquals(ChangePackageCurrentField.values().length,
                ChangePackageCurrentStateJdbcMapper.fieldCount());
        assertEquals(ChangePackageCurrentField.values().length, columns.length);
        assertEquals(ChangePackageCurrentField.values().length, assignments.length);
        assertEquals("objective", columns[0]);
        assertEquals("cleanup_plan_json", columns[columns.length - 1]);
        assertEquals("objective=?", assignments[0]);
        assertEquals("cleanup_plan_json=?", assignments[assignments.length - 1]);
        assertEquals(52, ChangePackageCurrentStateJdbcMapper.placeholders(52).split(", ").length);
    }

    @Test
    void mapsSnakeCaseAndLegacyCamelCaseRows() {
        ChangePackageCurrentState state = ChangePackageCurrentStateJdbcMapper.fromRow(Map.of(
                "risk_level", "MEDIUM",
                "objective", "repair payment",
                "repairWorkspaceId", "workspace-1"));

        assertEquals("MEDIUM", state.value(ChangePackageCurrentField.RISK_LEVEL));
        assertEquals("repair payment", state.value(ChangePackageCurrentField.OBJECTIVE));
        assertEquals("workspace-1", state.nullable(ChangePackageCurrentField.REPAIR_WORKSPACE_ID));
    }
}
