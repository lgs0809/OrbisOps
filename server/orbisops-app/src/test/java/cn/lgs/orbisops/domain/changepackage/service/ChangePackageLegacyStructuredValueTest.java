package cn.lgs.orbisops.domain.changepackage.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChangePackageLegacyStructuredValueTest {

    @Test
    void decodesLegacyJsonObjectsAndArrays() {
        assertEquals(Map.of("workspaceId", "workspace-1"),
                ChangePackageLegacyStructuredValue.decode(
                        "{\"workspaceId\":\"workspace-1\"}"));
        assertEquals(List.of("a", "b"),
                ChangePackageLegacyStructuredValue.decode("[\"a\",\"b\"]"));
    }

    @Test
    void preservesAlreadyTypedAndPlainValues() {
        Map<String, Object> typed = Map.of("status", "LANDED");
        assertSame(typed, ChangePackageLegacyStructuredValue.decode(typed));
        assertEquals("plain-text", ChangePackageLegacyStructuredValue.decode("plain-text"));
        assertEquals(" ", ChangePackageLegacyStructuredValue.decode(" "));
        assertEquals(null, ChangePackageLegacyStructuredValue.decode(null));
    }
}
