package cn.lgs.orbisops.trigger.application.project;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsProjectCapabilityMetadataPolicyTest {

    @Test
    void enrichesMysqlPermissionWithCrossTableJoinMetadata() {
        Map<String, Object> permission = OpsProjectCapabilityMetadataPolicy.enrichPermission(
                "mysql", Map.of("allowJoin", true, "actions", List.of("SELECT")));

        @SuppressWarnings("unchecked")
        Map<String, Object> multiObject =
                (Map<String, Object>) permission.get("multiObjectPermission");
        assertEquals(true, multiObject.get("enabled"));
        assertEquals("cross_table_join", multiObject.get("key"));
        assertEquals(List.of("SELECT"), permission.get("actions"));
    }

    @Test
    void attachesReadOnlyRemoteToolMetadataToTransportConfig() {
        Map<String, Object> transport = OpsProjectCapabilityMetadataPolicy.enrichTransportMetadata(
                "redis",
                List.of("READ"),
                Map.of("endpoint", "redis://localhost:6379/0"));

        assertEquals("redis://localhost:6379/0", transport.get("endpoint"));
        assertTrue(transport.containsKey("remoteToolMetadata"));
        assertTrue(transport.containsKey("remoteTools"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) transport.get("remoteToolMetadata");
        assertTrue(metadata.containsKey("redis_get"));
        assertTrue(metadata.containsKey("redis_scan"));
    }

    @Test
    void generatedRemoteToolMetadataSerializesAsPureValueTree() {
        Map<String, Object> transport = OpsProjectCapabilityMetadataPolicy.enrichTransportMetadata(
                "mysql",
                List.of("READ"),
                Map.of("endpoint", "mysql://localhost:3306/app"));

        String json = JSON.toJSONString(transport);
        assertFalse(json.contains("\"$ref\""));
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTrip = JSON.parseObject(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remoteTools = (List<Map<String, Object>>) roundTrip.get("remoteTools");
        assertEquals(List.of("READ"), remoteTools.get(0).get("allowedActions"));
    }

    @Test
    void leavesTransportConfigWithoutRemoteToolsWhenReadIsNotAllowed() {
        Map<String, Object> transport = OpsProjectCapabilityMetadataPolicy.enrichTransportMetadata(
                "mysql",
                List.of("WRITE"),
                Map.of("endpoint", "mysql://localhost:3306/app"));

        assertEquals("mysql://localhost:3306/app", transport.get("endpoint"));
        assertFalse(transport.containsKey("remoteToolMetadata"));
        assertFalse(transport.containsKey("remoteTools"));
    }
}
