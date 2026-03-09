package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLocalToolExecutionHandlerRegistryTest {

    @Test
    void routesEveryRegisteredAdapterWithCaseInsensitiveLookup() {
        RecordingHandler mysql = handler("LOCAL_MYSQL");
        RecordingHandler redis = handler("LOCAL_REDIS");
        OpsLocalToolExecutionHandlerRegistry registry =
                new OpsLocalToolExecutionHandlerRegistry(List.of(mysql, redis));

        Map<String, Object> result = registry.execute(
                " local_mysql ", "mysql_show_tables", new OpsLocalToolArguments(Map.of("database", "ops")));

        assertEquals("LOCAL_MYSQL", result.get("adapterType"));
        assertEquals("mysql_show_tables", result.get("toolName"));
        assertEquals("ops", result.get("database"));
        assertSame(mysql, registry.require("LOCAL_MYSQL"));
        assertEquals(Set.of("LOCAL_MYSQL", "LOCAL_REDIS"),
                registry.registeredAdapterTypes());
    }

    @Test
    void duplicateNormalizedAdapterTypeFailsAtConstruction() {
        RecordingHandler first = handler("LOCAL_MYSQL");
        RecordingHandler duplicate = handler(" local_mysql ");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsLocalToolExecutionHandlerRegistry(List.of(first, duplicate)));

        assertTrue(error.getMessage().contains("LOCAL_ADAPTER_HANDLER_DUPLICATE"));
        assertTrue(error.getMessage().contains("LOCAL_MYSQL"));
    }

    @Test
    void unknownAdapterFailsClosed() {
        OpsLocalToolExecutionHandlerRegistry registry =
                new OpsLocalToolExecutionHandlerRegistry(List.of(
                        handler("LOCAL_REDIS")));

        SecurityException error = assertThrows(SecurityException.class,
                () -> registry.require("LOCAL_PRODUCTION_MUTATOR"));

        assertTrue(error.getMessage().contains("LOCAL_ADAPTER_NOT_IMPLEMENTED"));
    }

    @Test
    void handlerExceptionIsNotSwallowedOrTranslated() {
        IllegalStateException expected = new IllegalStateException("HANDLER_FAILURE");
        OpsLocalToolExecutionHandler failing = new OpsLocalToolExecutionHandler() {
            @Override
            public Set<String> supportedAdapterTypes() {
                return Set.of("LOCAL_LOG");
            }

            @Override
            public Map<String, Object> execute(
                    String adapterType,
                    String toolName,
                    OpsLocalToolArguments arguments) {
                throw expected;
            }
        };
        OpsLocalToolExecutionHandlerRegistry registry =
                new OpsLocalToolExecutionHandlerRegistry(List.of(failing));

        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> registry.execute("LOCAL_LOG", "tail_log", new OpsLocalToolArguments(Map.of()))));
    }

    private RecordingHandler handler(String type) {
        return new RecordingHandler(type);
    }

    private static final class RecordingHandler implements OpsLocalToolExecutionHandler {
        private final String type;

        private RecordingHandler(String type) {
            this.type = type;
        }

        @Override
        public Set<String> supportedAdapterTypes() {
            return Set.of(type);
        }

        @Override
        public Map<String, Object> execute(
                String adapterType,
                String toolName,
                OpsLocalToolArguments arguments) {
            return Map.of(
                    "adapterType", adapterType,
                    "toolName", toolName,
                    "database", arguments.text("database"));
        }
    }
}
