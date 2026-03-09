package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectResourcePreparation;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectResourcePreparationServiceTest {

    @Test
    void createsDefaultMysqlPermissionFromLiveSchema() {
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsProjectResourceCredentialPolicy credentialPolicy =
                new OpsProjectResourceCredentialPolicy(secretResolver);
        OpsProjectResourceSchemaScanner scanner = mock(OpsProjectResourceSchemaScanner.class);
        when(scanner.scan(eq("mysql"), eq("mysql://db.example:3306/orders"), anyMap()))
                .thenReturn(Map.of(
                        "source", "live",
                        "objects", List.of(
                                Map.of("name", "orders"),
                                Map.of("name", "order_items"))));
        OpsProjectResourcePreparationService service =
                new OpsProjectResourcePreparationService(credentialPolicy, scanner);

        ProjectResourcePreparation preparation = service.prepare(
                new ProjectResourcePreparationRequest(
                        "mysql",
                        "orders-db",
                        "mysql://db.example:3306/orders",
                        Map.of("username", "reader"),
                        Map.of(),
                        Map.of(),
                        false));

        assertEquals("MySQL", preparation.typeName());
        assertEquals("SCANNED", preparation.status());
        assertEquals(List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN"),
                preparation.permission().get("actions"));
        assertEquals(List.of("orders", "order_items"),
                preparation.permission().get("objects"));
        assertEquals(15, preparation.permission().get("timeoutSeconds"));
    }

    @Test
    void refreshesPartialPermissionAgainstCurrentSchema() {
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsProjectResourceCredentialPolicy credentialPolicy =
                new OpsProjectResourceCredentialPolicy(secretResolver);
        OpsProjectResourceSchemaScanner scanner = mock(OpsProjectResourceSchemaScanner.class);
        when(scanner.scan(eq("elasticsearch"), eq("http://es.example:9200"), anyMap()))
                .thenReturn(Map.of(
                        "source", "live",
                        "objects", List.of(
                                Map.of("name", "logs-current"),
                                Map.of("name", "traces-current"))));
        OpsProjectResourcePreparationService service =
                new OpsProjectResourcePreparationService(credentialPolicy, scanner);

        ProjectResourcePreparation preparation = service.prepare(
                new ProjectResourcePreparationRequest(
                        "elasticsearch",
                        "logs-es",
                        "http://es.example:9200",
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "actions", List.of("SEARCH_INDEX"),
                                "objects", List.of("logs-current", "logs-removed"),
                                "allowJoin", true),
                        true));

        assertEquals(List.of("logs-current"), preparation.permission().get("objects"));
        assertEquals(List.of("SEARCH_INDEX"), preparation.permission().get("actions"));
        @SuppressWarnings("unchecked")
        Map<String, Object> multiObject =
                (Map<String, Object>) preparation.permission().get("multiObjectPermission");
        assertEquals("cross_index_search", multiObject.get("key"));
        assertTrue(Boolean.TRUE.equals(multiObject.get("enabled")));
    }
}
