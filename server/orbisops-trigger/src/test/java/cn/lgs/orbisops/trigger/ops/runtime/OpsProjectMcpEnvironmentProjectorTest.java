package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsProjectMcpEnvironmentProjectorTest {

    @Test
    void mysqlResourceMustProjectDatabaseAndTableAllowlistIntoReadonlyMcpEnvironment() {
        OpsProjectMcpTypePolicy typePolicy = new OpsProjectMcpTypePolicy();
        OpsProjectMcpEnvironmentProjector projector = new OpsProjectMcpEnvironmentProjector(
                typePolicy,
                new OpsProjectMcpEndpointParser(typePolicy));

        Map<String, String> env = projector.project(
                "mysql",
                "mysql://127.0.0.1:23307/demo_db",
                Map.of("username", "demo_readonly", "password", "secret-ref-value"),
                Map.of(
                        "objects", List.of("demo_order", "demo_db.sku"),
                        "maxRows", 100));

        assertEquals("demo_db", env.get("MYSQL_DATABASE"));
        assertEquals("demo_db", env.get("MYSQL_MCP_ALLOWED_DATABASES"));
        assertEquals(
                "demo_db.demo_order,demo_db.sku",
                env.get("MYSQL_MCP_ALLOWED_TABLES"));
        assertEquals("demo_readonly", env.get("MYSQL_USER"));
    }
}
