package cn.lgs.orbisops.application.changepackage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangePackageQueryServiceTest {

    @Test
    void nullableOptionalProjectionFieldsDoNotBreakProductQueueView() {
        ChangePackageQueryPort port = mock(ChangePackageQueryPort.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("packageId", "cp-1");
        row.put("status", "DRAFT");
        row.put("incidentId", null);
        when(port.list(any(ChangePackageListQuery.class))).thenReturn(List.of(row));
        when(port.detail("cp-1")).thenReturn(row);
        ChangePackageQueryService service = new ChangePackageQueryService(port);

        Map<String, Object> listed = service.list(
                new ChangePackageListQuery(Map.of(), 10)).get(0);
        Map<String, Object> detail = service.detail("cp-1");

        assertNull(listed.get("incidentId"));
        assertNull(detail.get("incidentId"));
        assertEquals(listed.get("productQueue"), detail.get("productQueue"));
        assertThrows(UnsupportedOperationException.class,
                () -> listed.put("status", "APPROVED"));
    }
}
