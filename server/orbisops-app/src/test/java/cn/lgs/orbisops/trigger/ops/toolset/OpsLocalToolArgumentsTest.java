package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsLocalToolArgumentsTest {

    @Test
    void argumentsMustPreserveRawValuesIncludingNullAndExposeTextFallbacks() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("number", 12);
        input.put("blank", " ");
        input.put("nullable", null);
        OpsLocalToolArguments args = new OpsLocalToolArguments(input);

        assertEquals(12, args.raw("number"));
        assertEquals("12", args.text("number"));
        assertEquals("fallback", args.text("blank", "fallback"));
        assertNull(args.raw("nullable"));
        assertThrows(UnsupportedOperationException.class,
                () -> args.asMap().put("changed", true));
    }

    @Test
    void requiredAndBoundedIntMustPreserveStableInputRules() {
        OpsLocalToolArguments args = new OpsLocalToolArguments(Map.of(
                "value", "present",
                "high", "999",
                "bad", "abc"));

        assertEquals("present", args.required("value", "missing"));
        assertEquals(20, args.boundedInt("high", 1, 20, 8));
        assertEquals(8, args.boundedInt("bad", 1, 20, 8));
        assertThrows(IllegalArgumentException.class,
                () -> args.required("absent", "required-value"));
    }
}
