package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsToolSchemaNormalizerTest {

    @Test
    void objectSchemaAlwaysContainsPropertiesForOpenAiCompatibleProviders() {
        ToolDefinition normalized = OpsToolSchemaNormalizer.normalize(ToolDefinition.builder()
                .name("empty_tool")
                .description("empty input")
                .inputSchema("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"additionalProperties\":false}")
                .build());

        JSONObject schema = JSON.parseObject(normalized.inputSchema());
        assertEquals("object", schema.getString("type"));
        assertNotNull(schema.getJSONObject("properties"));
        assertEquals(0, schema.getJSONObject("properties").size());
    }

    @Test
    void malformedSchemaFailsClosed() {
        ToolDefinition malformed = ToolDefinition.builder()
                .name("bad_tool")
                .description("bad input")
                .inputSchema("not-json")
                .build();
        assertThrows(IllegalArgumentException.class, () -> OpsToolSchemaNormalizer.normalize(malformed));
    }
}
