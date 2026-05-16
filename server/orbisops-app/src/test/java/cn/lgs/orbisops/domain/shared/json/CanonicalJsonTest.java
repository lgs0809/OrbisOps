package cn.lgs.orbisops.domain.shared.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalJsonTest {

    @Test
    void shouldSerializeMapsInStableKeyOrderAndRoundTripNestedValues() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("z", List.of("first", Map.of("enabled", true)));
        input.put("a", new BigDecimal("10.50"));

        String serialized = CanonicalJson.stringify(input);

        assertThat(serialized).isEqualTo("{\"a\":10.50,\"z\":[\"first\",{\"enabled\":true}]}");
        assertThat(CanonicalJson.parseObject(serialized))
                .containsEntry("a", new BigDecimal("10.50"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateADeepMutableCopyForSnapshotConstruction() {
        List<Object> nested = new ArrayList<>(List.of("before"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("nested", nested);

        Map<String, Object> copy = CanonicalJson.copyObject(input);
        ((List<Object>) copy.get("nested")).add("after");

        assertThat(nested).containsExactly("before");
        assertThat((List<Object>) copy.get("nested")).containsExactly("before", "after");
    }

    @Test
    void shouldPreserveInsertionOrderForPersistedPayloads() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", 2);
        nested.put("y", 3);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("b", 1);
        payload.put("a", nested);

        assertThat(CanonicalJson.stringifyPreservingOrder(payload))
                .isEqualTo("{\"b\":1,\"a\":{\"z\":2,\"y\":3}}");
        assertThat(CanonicalJson.stringify(payload))
                .isEqualTo("{\"a\":{\"y\":3,\"z\":2},\"b\":1}");
    }

    @Test
    void shouldEscapeStringsParseNumbersAndSerializeRecords() {
        String serialized = CanonicalJson.stringifyPreservingOrder(Map.of(
                "text", "line\n\"quoted\"\\tab\t"));

        assertThat(serialized).contains("line\\n\\\"quoted\\\"\\\\tab\\t");
        assertThat(CanonicalJson.parseArray("[0,-1,2147483648,1.25,1e3]"))
                .containsExactly(0, -1, 2147483648L, new BigDecimal("1.25"), new BigDecimal("1e3"));
        assertThat(CanonicalJson.stringify(new Sample("skill", 2)))
                .isEqualTo("{\"count\":2,\"name\":\"skill\"}");
        assertThat(CanonicalJson.stringifyPreservingOrder(new Sample("skill", 2)))
                .isEqualTo("{\"name\":\"skill\",\"count\":2}");
    }

    @Test
    void shouldRejectNonObjectAndNonArrayInputs() {
        assertThatThrownBy(() -> CanonicalJson.parseObject("[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON_OBJECT_REQUIRED");
        assertThatThrownBy(() -> CanonicalJson.parseArray("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON_ARRAY_REQUIRED");
        assertThatThrownBy(() -> CanonicalJson.parse("{\"a\":1} trailing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("INVALID_JSON_AT_POSITION:");
    }

    private record Sample(String name, int count) {
    }
}
