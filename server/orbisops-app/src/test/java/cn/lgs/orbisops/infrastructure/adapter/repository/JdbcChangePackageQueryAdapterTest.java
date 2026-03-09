package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackageQueryAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void adminListProjectsApprovedLegacyRowWithoutSnapshotAsInvalidInsteadOfThrowing() {
        IChangePackageCurrentRepository currents = mock(IChangePackageCurrentRepository.class);
        IChangePackageVersionRepository versions = mock(IChangePackageVersionRepository.class);
        IChangePackageEventRepository events = mock(IChangePackageEventRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("package_id", "cp-legacy");
        row.put("project_id", "demo-project");
        row.put("status", "NEEDS_REPLAN");
        row.put("version", 1);
        row.put("package_hash", "current-hash");
        row.put("approved_version", 1);
        row.put("approved_package_hash", "approved-hash");
        row.put("approved_snapshot_json", "");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        JdbcChangePackageQueryAdapter adapter = new JdbcChangePackageQueryAdapter(currents, versions, events, provider);

        List<Map<String, Object>> result = adapter.list(new ChangePackageListQuery(
                Map.of("projectId", "demo-project"), 200));

        assertEquals(1, result.size());
        Map<String, Object> item = result.get(0);
        assertEquals("cp-legacy", item.get("packageId"));
        assertTrue((Boolean) item.get("legacyInvalid"));
        assertEquals("CHANGE_PACKAGE_VERSION_SNAPSHOT_REQUIRED", item.get("invalidReason"));
        assertFalse((Boolean) item.get("runtimeExecutable"));
        verify(currents, never()).findAll(any());
    }
}
