package cn.lgs.orbisops.domain.audit;

import cn.lgs.orbisops.domain.audit.service.ConfigAuditMaskingPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAuditMaskingPolicyTest {

    @Test
    void masksJsonAssignmentUrlCredentialQueryAndBearerShapes() {
        String masked = new ConfigAuditMaskingPolicy().mask("""
                {"api_key":"sk-plain","dbPassword":"db-secret","webhook_secret":"hook-secret",
                "url":"mysql://ops:db-pass@127.0.0.1:3306/demo_db?password=query-secret",
                "Authorization":"Bearer token-value"}
                """);

        assertTrue(masked.contains("\"api_key\":\"******\""));
        assertTrue(masked.contains("\"dbPassword\":\"******\""));
        assertTrue(masked.contains("\"webhook_secret\":\"******\""));
        assertTrue(masked.contains("mysql://ops:******@127.0.0.1:3306/demo_db?password=******"));
        assertTrue(masked.contains("\"Authorization\":\"******\""));
        assertFalse(masked.contains("sk-plain"));
        assertFalse(masked.contains("db-secret"));
        assertFalse(masked.contains("hook-secret"));
        assertFalse(masked.contains("db-pass"));
        assertFalse(masked.contains("query-secret"));
        assertFalse(masked.contains("token-value"));
    }
}
